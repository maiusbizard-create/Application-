package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SepaTransaction
import com.example.data.model.UserWallet
import com.example.ui.components.StripeConfigurationBanner
import com.example.ui.components.TransactionRowCard
import com.example.ui.viewmodel.BankActionState
import com.example.ui.viewmodel.UiState
import com.example.util.IbanValidator

enum class BankFlowMode { DEPOSIT, WITHDRAWAL }

@Composable
fun BankSepaScreen(
    initialMode: BankFlowMode,
    walletState: UiState<UserWallet?>,
    transactionsState: UiState<List<SepaTransaction>>,
    bankActionState: BankActionState,
    isStripeConfigured: Boolean,
    isStripeTestMode: Boolean,
    refreshingTxId: String?,
    onDeposit: (holderName: String, iban: String, amountEuro: String) -> Unit,
    onWithdraw: (holderName: String, iban: String, amountEuro: String) -> Unit,
    onRefreshTx: (SepaTransaction) -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wallet = (walletState as? UiState.Success)?.data
    var mode by remember(initialMode) { mutableStateOf(initialMode) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Dépôt & Retrait SEPA",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Flux bancaires réels par IBAN via Stripe dès 10,00 €",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_sepa_iban_shield),
                        contentDescription = "Sécurité SEPA Stripe IBAN",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.3f))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Solde disponible",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = IbanValidator.formatEuroFromCents(wallet?.balanceCents ?: 0L),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Minimum dépôt / retrait : 10,00 €",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }
            }

            item {
                StripeConfigurationBanner(
                    isConfigured = isStripeConfigured,
                    isTestMode = isStripeTestMode,
                )
            }

            // Segmented Switcher
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BankFlowMode.entries.forEach { tabMode ->
                        val selected = mode == tabMode
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    mode = tabMode
                                    onClearFeedback()
                                }
                                .testTag(
                                    if (tabMode == BankFlowMode.DEPOSIT) "tab_deposit_mode"
                                    else "tab_withdraw_mode"
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (tabMode == BankFlowMode.DEPOSIT) "Dépôt IBAN" else "Retrait IBAN",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                if (mode == BankFlowMode.DEPOSIT) {
                    SepaDepositFormCard(
                        initialHolderName = wallet?.displayName.orEmpty(),
                        bankActionState = bankActionState,
                        isStripeTestMode = isStripeTestMode,
                        onSubmitSepaDeposit = onDeposit,
                        onInputChanged = onClearFeedback,
                    )
                } else {
                    SepaWithdrawalFormCard(
                        availableBalanceCents = wallet?.balanceCents ?: 0L,
                        initialHolderName = wallet?.displayName.orEmpty(),
                        bankActionState = bankActionState,
                        isStripeTestMode = isStripeTestMode,
                        onSubmitSepaWithdrawal = onWithdraw,
                        onInputChanged = onClearFeedback,
                    )
                }
            }

            item {
                Text(
                    text = "Historique complet SEPA",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            when (transactionsState) {
                UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = transactionsState.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val txs = transactionsState.data
                    if (txs.isEmpty()) {
                        item {
                            Text(
                                text = "Aucune opération enregistrée.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(txs, key = { it.id }) { tx ->
                            TransactionRowCard(
                                tx = tx,
                                isRefreshing = refreshingTxId == tx.id,
                                onRefreshStatus = { onRefreshTx(tx) },
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
