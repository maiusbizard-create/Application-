package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AiAnalysisRecord
import com.example.data.model.BotChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.RiskProfile
import com.example.data.model.RobotStrategyPlan
import com.example.data.model.SepaTransaction
import com.example.data.model.TxStatus
import com.example.data.model.TxType
import com.example.data.model.UserWallet
import com.example.data.stripe.StripeRepository
import com.example.util.IbanValidator
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class WalletRepository(
    private val db: FirebaseFirestore,
    private val stripeRepository: StripeRepository = StripeRepository(),
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Authentification Google requise avant d'accéder au portefeuille.")

    fun isStripeConfigured(): Boolean = stripeRepository.isStripeConfigured()

    fun isStripeTestMode(): Boolean = stripeRepository.isTestMode()

    suspend fun ensureWalletInitialized(
        userId: String,
        displayName: String,
        email: String,
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Utilisateur incohérent." }
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName.trim().ifEmpty { "Investisseur KairoIA" }.take(80)
                val safeEmail = email.trim().ifEmpty { "user@kairoia.app" }.take(120)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "balanceCents" to 0L,
                    "pendingDepositCents" to 0L,
                    "botActive" to true,
                    "botRiskProfile" to RiskProfile.EQUILIBRE.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeWallet(userId: String): Flow<UserWallet?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Accès interdit." }
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserWallet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
        )
    }

    fun observeTransactions(userId: String): Flow<List<SepaTransaction>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Accès interdit." }
        emitAll(
            db.collection("users").document(uid).collection("transactions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SepaTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
        )
    }

    fun observeAnalyses(userId: String): Flow<List<AiAnalysisRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Accès interdit." }
        emitAll(
            db.collection("users").document(uid).collection("ai_analyses")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AiAnalysisRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
        )
    }

    fun observeBotMessages(userId: String): Flow<List<BotChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Accès interdit." }
        emitAll(
            db.collection("users").document(uid).collection("bot_messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(BotChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
        )
    }

    suspend fun updateBotSettings(
        userId: String,
        botActive: Boolean,
        riskProfile: RiskProfile,
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Utilisateur incohérent." }
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "botActive" to botActive,
                        "botRiskProfile" to riskProfile.name,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun executeRealSepaDeposit(
        userId: String,
        wallet: UserWallet,
        holderName: String,
        iban: String,
        amountCents: Long,
    ): Result<SepaTransaction> {
        val txRef = db.collection("users").document(userId).collection("transactions").document()
        val userRef = db.collection("users").document(userId)
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Utilisateur incohérent." }
            require(amountCents in IbanValidator.MIN_SEPA_AMOUNT_CENTS..IbanValidator.MAX_SEPA_AMOUNT_CENTS) {
                "Le montant du dépôt doit être d'au moins 10,00 €."
            }
            val safeHolder = holderName.trim().take(80)
            require(safeHolder.length >= 2) { "Nom du titulaire requis (minimum 2 caractères)." }

            val stripeResult = stripeRepository.createSepaDeposit(
                userId = uid,
                existingCustomerId = wallet.stripeCustomerId,
                email = wallet.email,
                holderName = safeHolder,
                iban = iban,
                amountCents = amountCents,
            ).getOrThrow()

            val mappedStatus = when (stripeResult.status.lowercase()) {
                "succeeded" -> TxStatus.SUCCEEDED
                "canceled" -> TxStatus.CANCELED
                "requires_payment_method" -> TxStatus.FAILED
                else -> TxStatus.PROCESSING
            }

            val creditImmediately = mappedStatus == TxStatus.SUCCEEDED
            val maskedIban = IbanValidator.maskIban(iban)

            db.runTransaction { transaction ->
                val walletSnap = transaction.get(userRef)
                val currentBalance = walletSnap.getLong("balanceCents") ?: 0L
                val currentPending = walletSnap.getLong("pendingDepositCents") ?: 0L

                val txPayload = mapOf(
                    "userId" to uid,
                    "type" to TxType.DEPOSIT.name,
                    "amountCents" to amountCents,
                    "currency" to "EUR",
                    "ibanMasked" to maskedIban,
                    "accountHolderName" to safeHolder,
                    "stripeId" to stripeResult.paymentIntentId,
                    "status" to mappedStatus.name,
                    "balanceCredited" to creditImmediately,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                transaction.set(txRef, txPayload)

                val newBalance = if (creditImmediately) currentBalance + amountCents else currentBalance
                val newPending = if (mappedStatus == TxStatus.PROCESSING) currentPending + amountCents else currentPending
                transaction.update(
                    userRef,
                    mapOf(
                        "stripeCustomerId" to stripeResult.customerId,
                        "savedIbanMasked" to maskedIban,
                        "balanceCents" to newBalance,
                        "pendingDepositCents" to newPending,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }.await()

            Result.success(
                SepaTransaction(
                    id = txRef.id,
                    userId = uid,
                    type = TxType.DEPOSIT.name,
                    amountCents = amountCents,
                    currency = "EUR",
                    ibanMasked = maskedIban,
                    accountHolderName = safeHolder,
                    stripeId = stripeResult.paymentIntentId,
                    status = mappedStatus.name,
                    balanceCredited = creditImmediately,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            Result.failure(e)
        }
    }

    suspend fun executeRealSepaWithdrawal(
        userId: String,
        holderName: String,
        iban: String,
        amountCents: Long,
    ): Result<SepaTransaction> {
        val txRef = db.collection("users").document(userId).collection("transactions").document()
        val userRef = db.collection("users").document(userId)
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Utilisateur incohérent." }
            require(amountCents in IbanValidator.MIN_SEPA_AMOUNT_CENTS..IbanValidator.MAX_SEPA_AMOUNT_CENTS) {
                "Le montant minimum de retrait est de 10,00 €."
            }
            val safeHolder = holderName.trim().take(80)
            require(safeHolder.length >= 2) { "Nom du bénéficiaire requis (minimum 2 caractères)." }

            // Pre-check available balance before hitting Stripe Payout API
            val currentWalletSnap = userRef.get().await()
            val availableCents = currentWalletSnap.getLong("balanceCents") ?: 0L
            require(availableCents >= amountCents) {
                "Solde disponible insuffisant (${IbanValidator.formatEuroFromCents(availableCents)})."
            }

            val stripePayout = stripeRepository.createSepaWithdrawal(
                userId = uid,
                holderName = safeHolder,
                iban = iban,
                amountCents = amountCents,
            ).getOrThrow()

            val mappedStatus = when (stripePayout.status.lowercase()) {
                "paid" -> TxStatus.SUCCEEDED
                "failed" -> TxStatus.FAILED
                "canceled" -> TxStatus.CANCELED
                else -> TxStatus.PROCESSING
            }

            val maskedIban = IbanValidator.maskIban(iban)

            db.runTransaction { transaction ->
                val walletSnap = transaction.get(userRef)
                val currentBalance = walletSnap.getLong("balanceCents") ?: 0L
                if (currentBalance < amountCents) {
                    throw IllegalStateException("Solde insuffisant lors de la validation du retrait.")
                }

                val txPayload = mapOf(
                    "userId" to uid,
                    "type" to TxType.WITHDRAWAL.name,
                    "amountCents" to amountCents,
                    "currency" to "EUR",
                    "ibanMasked" to maskedIban,
                    "accountHolderName" to safeHolder,
                    "stripeId" to stripePayout.payoutId,
                    "status" to mappedStatus.name,
                    "balanceCredited" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                transaction.set(txRef, txPayload)

                transaction.update(
                    userRef,
                    mapOf(
                        "savedIbanMasked" to maskedIban,
                        "balanceCents" to (currentBalance - amountCents),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }.await()

            Result.success(
                SepaTransaction(
                    id = txRef.id,
                    userId = uid,
                    type = TxType.WITHDRAWAL.name,
                    amountCents = amountCents,
                    currency = "EUR",
                    ibanMasked = maskedIban,
                    accountHolderName = safeHolder,
                    stripeId = stripePayout.payoutId,
                    status = mappedStatus.name,
                    balanceCredited = true,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            Result.failure(e)
        }
    }

    suspend fun refreshStripeTransactionStatus(
        userId: String,
        tx: SepaTransaction,
    ): Result<TxStatus> {
        val txRef = db.collection("users").document(userId).collection("transactions").document(tx.id)
        val userRef = db.collection("users").document(userId)
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Utilisateur incohérent." }
            if (tx.statusEnum != TxStatus.PROCESSING) {
                return Result.success(tx.statusEnum)
            }

            val (newStatus, failureReason) = if (tx.typeEnum == TxType.DEPOSIT) {
                val piStatus = stripeRepository.fetchPaymentIntentStatus(tx.stripeId).getOrThrow()
                val mapped = when (piStatus.lowercase()) {
                    "succeeded" -> TxStatus.SUCCEEDED
                    "canceled" -> TxStatus.CANCELED
                    "requires_payment_method" -> TxStatus.FAILED
                    else -> TxStatus.PROCESSING
                }
                mapped to null
            } else {
                val (poStatus, errMsg) = stripeRepository.fetchPayoutStatus(tx.stripeId).getOrThrow()
                val mapped = when (poStatus.lowercase()) {
                    "paid" -> TxStatus.SUCCEEDED
                    "failed" -> TxStatus.FAILED
                    "canceled" -> TxStatus.CANCELED
                    else -> TxStatus.PROCESSING
                }
                mapped to errMsg?.take(300)
            }

            if (newStatus != TxStatus.PROCESSING) {
                db.runTransaction { transaction ->
                    val walletSnap = transaction.get(userRef)
                    val txSnap = transaction.get(txRef)
                    val currentStatus = TxStatus.fromRaw(txSnap.getString("status"))
                    if (currentStatus != TxStatus.PROCESSING) return@runTransaction

                    val currentBalance = walletSnap.getLong("balanceCents") ?: 0L
                    val currentPending = walletSnap.getLong("pendingDepositCents") ?: 0L
                    val alreadyCredited = txSnap.getBoolean("balanceCredited") ?: false

                    val updateMap = mutableMapOf<String, Any>(
                        "status" to newStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                    if (failureReason != null) {
                        updateMap["failureReason"] = failureReason
                    }

                    if (tx.typeEnum == TxType.DEPOSIT) {
                        val updatedPending = (currentPending - tx.amountCents).coerceAtLeast(0L)
                        if (newStatus == TxStatus.SUCCEEDED && !alreadyCredited) {
                            updateMap["balanceCredited"] = true
                            transaction.update(
                                userRef,
                                mapOf(
                                    "balanceCents" to (currentBalance + tx.amountCents),
                                    "pendingDepositCents" to updatedPending,
                                    "updatedAt" to FieldValue.serverTimestamp(),
                                )
                            )
                        } else {
                            transaction.update(
                                userRef,
                                mapOf(
                                    "pendingDepositCents" to updatedPending,
                                    "updatedAt" to FieldValue.serverTimestamp(),
                                )
                            )
                        }
                    } else if (newStatus == TxStatus.FAILED || newStatus == TxStatus.CANCELED) {
                        // Refund wallet if Stripe payout failed or was canceled
                        transaction.update(
                            userRef,
                            mapOf(
                                "balanceCents" to (currentBalance + tx.amountCents),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                    }

                    transaction.update(txRef, updateMap)
                }.await()
            }

            Result.success(newStatus)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, txRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveAiAnalysis(
        userId: String,
        riskProfile: RiskProfile,
        plan: RobotStrategyPlan,
    ): Result<Unit> {
        val ref = db.collection("users").document(userId).collection("ai_analyses").document()
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Utilisateur incohérent." }
            val allocationsFormatted = plan.allocations.joinToString("\n") {
                "• ${it.assetName} (${it.percentage}%) — ${it.rationale}"
            }.take(3000).ifEmpty { "Portefeuille en attente d'allocation" }

            val payload = mapOf(
                "userId" to uid,
                "riskProfile" to riskProfile.name,
                "recommendedAction" to plan.recommendedAction.take(60).ifEmpty { "CONSERVER" },
                "riskScore" to plan.riskScore.coerceIn(1, 10).toLong(),
                "projectedYieldPercent" to plan.projectedAnnualYieldPercent.coerceIn(0.0, 100.0),
                "summary" to plan.summary.take(2000).ifEmpty { "Analyse générée." },
                "allocationsText" to allocationsFormatted,
                "createdAt" to FieldValue.serverTimestamp(),
            )
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun saveBotMessage(
        userId: String,
        sender: ChatSender,
        text: String,
    ): Result<Unit> {
        val ref = db.collection("users").document(userId).collection("bot_messages").document()
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Utilisateur incohérent." }
            val safeText = text.trim().take(4000)
            require(safeText.isNotEmpty()) { "Le message ne peut pas être vide." }
            ref.set(
                mapOf(
                    "userId" to uid,
                    "sender" to sender.name,
                    "text" to safeText,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }
}
