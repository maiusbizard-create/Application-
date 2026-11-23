package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiAnalysisRecord
import com.example.data.model.BotChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.RiskProfile
import com.example.data.model.RobotStrategyPlan
import com.example.data.model.SepaTransaction
import com.example.data.model.TxStatus
import com.example.data.model.UserWallet
import com.example.data.repository.AiRobotRepository
import com.example.data.repository.WalletRepository
import com.example.util.IbanValidator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiPlanState {
    data object Idle : AiPlanState
    data object Loading : AiPlanState
    data class Success(val plan: RobotStrategyPlan) : AiPlanState
    data class Error(val message: String) : AiPlanState
}

sealed interface BankActionState {
    data object Idle : BankActionState
    data class Loading(val label: String) : BankActionState
    data class Success(val tx: SepaTransaction, val message: String) : BankActionState
    data class Error(val message: String) : BankActionState
}

sealed interface ChatStreamState {
    data object Idle : ChatStreamState
    data class Streaming(val partialText: String) : ChatStreamState
    data class Error(val message: String) : ChatStreamState
}

class KairoViewModel(
    private val walletRepository: WalletRepository,
    private val aiRobotRepository: AiRobotRepository,
    private val currentUserId: String,
    private val displayName: String,
    private val email: String,
) : ViewModel() {

    val isStripeConfigured: Boolean = walletRepository.isStripeConfigured()
    val isStripeTestMode: Boolean = walletRepository.isStripeTestMode()

    val walletState: StateFlow<UiState<UserWallet?>> =
        walletRepository.observeWallet(currentUserId)
            .toUiStateFlow("Erreur de chargement du portefeuille")

    val transactionsState: StateFlow<UiState<List<SepaTransaction>>> =
        walletRepository.observeTransactions(currentUserId)
            .toUiStateFlow("Erreur de chargement des transactions")

    val analysesState: StateFlow<UiState<List<AiAnalysisRecord>>> =
        walletRepository.observeAnalyses(currentUserId)
            .toUiStateFlow("Erreur de chargement des analyses IA")

    val botMessagesState: StateFlow<UiState<List<BotChatMessage>>> =
        walletRepository.observeBotMessages(currentUserId)
            .toUiStateFlow("Erreur de chargement du chat KairoBot")

    private val _aiPlanState = MutableStateFlow<AiPlanState>(AiPlanState.Idle)
    val aiPlanState: StateFlow<AiPlanState> = _aiPlanState.asStateFlow()

    private val _bankActionState = MutableStateFlow<BankActionState>(BankActionState.Idle)
    val bankActionState: StateFlow<BankActionState> = _bankActionState.asStateFlow()

    private val _chatStreamState = MutableStateFlow<ChatStreamState>(ChatStreamState.Idle)
    val chatStreamState: StateFlow<ChatStreamState> = _chatStreamState.asStateFlow()

    private val _refreshingTxId = MutableStateFlow<String?>(null)
    val refreshingTxId: StateFlow<String?> = _refreshingTxId.asStateFlow()

    init {
        viewModelScope.launch {
            walletRepository.ensureWalletInitialized(
                userId = currentUserId,
                displayName = displayName,
                email = email,
            )
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackError: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackError, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: fallbackError))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    fun updateBotSettings(botActive: Boolean, riskProfile: RiskProfile) {
        viewModelScope.launch {
            walletRepository.updateBotSettings(currentUserId, botActive, riskProfile)
        }
    }

    fun generateAiAnalysis() {
        val wallet = (walletState.value as? UiState.Success)?.data ?: return
        val txs = (transactionsState.value as? UiState.Success)?.data ?: emptyList()

        viewModelScope.launch {
            _aiPlanState.value = AiPlanState.Loading
            try {
                val plan = aiRobotRepository.generatePortfolioAnalysis(wallet, txs).getOrThrow()
                _aiPlanState.value = AiPlanState.Success(plan)
                walletRepository.saveAiAnalysis(
                    userId = currentUserId,
                    riskProfile = wallet.riskProfileEnum,
                    plan = plan,
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI analysis timed out", e)
                _aiPlanState.value = AiPlanState.Error("L'analyse IA a expiré après 60 secondes. Veuillez réessayer.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI analysis failed", e)
                _aiPlanState.value = AiPlanState.Error(
                    e.localizedMessage ?: "Impossible de générer l'analyse IA."
                )
            }
        }
    }

    fun sendChatMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return
        val wallet = (walletState.value as? UiState.Success)?.data ?: return
        val txs = (transactionsState.value as? UiState.Success)?.data ?: emptyList()

        viewModelScope.launch {
            _chatStreamState.value = ChatStreamState.Streaming("")
            try {
                walletRepository.saveBotMessage(currentUserId, ChatSender.USER, prompt)
                var latestReply = ""
                aiRobotRepository.streamRobotReply(prompt, wallet, txs).collect { accumulated ->
                    latestReply = accumulated
                    _chatStreamState.value = ChatStreamState.Streaming(accumulated)
                }
                if (latestReply.isNotBlank()) {
                    walletRepository.saveBotMessage(currentUserId, ChatSender.BOT, latestReply)
                }
                _chatStreamState.value = ChatStreamState.Idle
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "KairoBot stream timed out", e)
                _chatStreamState.value = ChatStreamState.Error("Délai dépassé (60s) lors de la réponse de KairoBot.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "KairoBot stream failed", e)
                _chatStreamState.value = ChatStreamState.Error(
                    e.localizedMessage ?: "Erreur lors de la réponse de KairoBot."
                )
            }
        }
    }

    fun executeSepaDeposit(
        holderName: String,
        iban: String,
        amountEuroInput: String,
    ) {
        val wallet = (walletState.value as? UiState.Success)?.data ?: run {
            _bankActionState.value = BankActionState.Error("Portefeuille non encore chargé.")
            return
        }
        val cents = IbanValidator.parseEuroToCents(amountEuroInput)
        if (cents == null || cents < IbanValidator.MIN_SEPA_AMOUNT_CENTS) {
            _bankActionState.value = BankActionState.Error("Le montant minimum de dépôt est de 10,00 €.")
            return
        }
        if (!IbanValidator.isValidSepaIban(iban)) {
            _bankActionState.value = BankActionState.Error("IBAN SEPA invalide. Vérifiez le format (ex: FR76...).")
            return
        }

        viewModelScope.launch {
            _bankActionState.value = BankActionState.Loading("Initialisation du prélèvement SEPA Stripe...")
            val result = walletRepository.executeRealSepaDeposit(
                userId = currentUserId,
                wallet = wallet,
                holderName = holderName,
                iban = iban,
                amountCents = cents,
            )
            _bankActionState.value = result.fold(
                onSuccess = { tx ->
                    val statusHint = if (tx.statusEnum == TxStatus.SUCCEEDED) {
                        "Dépôt de ${IbanValidator.formatEuroFromCents(cents)} crédité immédiatement (${tx.stripeId})."
                    } else {
                        "Mandat SEPA créé (${tx.stripeId}). En cours de compensation bancaire."
                    }
                    BankActionState.Success(tx, statusHint)
                },
                onFailure = { err ->
                    BankActionState.Error(err.localizedMessage ?: "Échec du dépôt Stripe SEPA.")
                }
            )
        }
    }

    fun executeSepaWithdrawal(
        holderName: String,
        iban: String,
        amountEuroInput: String,
    ) {
        val cents = IbanValidator.parseEuroToCents(amountEuroInput)
        if (cents == null || cents < IbanValidator.MIN_SEPA_AMOUNT_CENTS) {
            _bankActionState.value = BankActionState.Error("Le montant minimum de retrait est de 10,00 €.")
            return
        }
        if (!IbanValidator.isValidSepaIban(iban)) {
            _bankActionState.value = BankActionState.Error("IBAN SEPA invalide. Vérifiez le format.")
            return
        }

        viewModelScope.launch {
            _bankActionState.value = BankActionState.Loading("Exécution du virement SEPA Stripe...")
            val result = walletRepository.executeRealSepaWithdrawal(
                userId = currentUserId,
                holderName = holderName,
                iban = iban,
                amountCents = cents,
            )
            _bankActionState.value = result.fold(
                onSuccess = { tx ->
                    BankActionState.Success(
                        tx,
                        "Retrait SEPA de ${IbanValidator.formatEuroFromCents(cents)} émis via Stripe (${tx.stripeId})."
                    )
                },
                onFailure = { err ->
                    BankActionState.Error(err.localizedMessage ?: "Échec du retrait Stripe SEPA.")
                }
            )
        }
    }

    fun refreshTransaction(tx: SepaTransaction) {
        viewModelScope.launch {
            _refreshingTxId.value = tx.id
            val result = walletRepository.refreshStripeTransactionStatus(currentUserId, tx)
            _refreshingTxId.value = null
            result.onFailure { err ->
                _bankActionState.value = BankActionState.Error(
                    err.localizedMessage ?: "Impossible de synchroniser le statut Stripe."
                )
            }
        }
    }

    fun clearBankActionFeedback() {
        _bankActionState.value = BankActionState.Idle
    }

    fun clearChatError() {
        if (_chatStreamState.value is ChatStreamState.Error) {
            _chatStreamState.value = ChatStreamState.Idle
        }
    }

    private companion object {
        const val TAG = "KairoViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
