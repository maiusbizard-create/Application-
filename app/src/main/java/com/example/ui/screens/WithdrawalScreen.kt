package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.UserWallet
import com.example.ui.components.SepaIbanTextField
import com.example.ui.components.StripeConfigurationBanner
import com.example.ui.viewmodel.BankActionState
import com.example.ui.viewmodel.UiState
import com.example.util.IbanValidator
import java.util.Locale

/**
 * Standalone WithdrawalScreen allowing a user to withdraw >= 10 EUR to a validated SEPA IBAN via Stripe Payouts.
 */
@Composable
fun WithdrawalScreen(
    walletState: UiState<UserWallet?>,
    bankActionState: BankActionState,
    isStripeConfigured: Boolean,
    isStripeTestMode: Boolean,
    onSubmitSepaWithdrawal: (holderName: String, iban: String, amountEuro: String) -> Unit,
    onClearFeedback: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }

    val wallet = (walletState as? UiState.Success)?.data

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("withdrawal_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Retour"
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Retrait SEPA Stripe",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Virement vers votre IBAN dès 10,00 €",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                StripeConfigurationBanner(
                    isConfigured = isStripeConfigured,
                    isTestMode = isStripeTestMode,
                )
            }

            item {
                SepaWithdrawalFormCard(
                    availableBalanceCents = wallet?.balanceCents ?: 0L,
                    initialHolderName = wallet?.displayName.orEmpty(),
                    bankActionState = bankActionState,
                    isStripeTestMode = isStripeTestMode,
                    onSubmitSepaWithdrawal = onSubmitSepaWithdrawal,
                    onInputChanged = onClearFeedback,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SepaWithdrawalFormCard(
    availableBalanceCents: Long,
    initialHolderName: String,
    bankActionState: BankActionState,
    isStripeTestMode: Boolean,
    onSubmitSepaWithdrawal: (holderName: String, iban: String, amountEuro: String) -> Unit,
    onInputChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var amountText by remember { mutableStateOf("10") }
    var ibanInput by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf(initialHolderName) }

    LaunchedEffect(initialHolderName) {
        if (holderName.isBlank() && initialHolderName.isNotBlank()) {
            holderName = initialHolderName
        }
    }

    val ibanValidation = remember(ibanInput) { IbanValidator.validateRealtime(ibanInput) }
    val isIbanValid = ibanValidation.isValid
    val parsedCents = IbanValidator.parseEuroToCents(amountText)
    val isAmountValid = parsedCents != null && parsedCents >= IbanValidator.MIN_SEPA_AMOUNT_CENTS
    val hasEnoughBalance = parsedCents != null && availableBalanceCents >= parsedCents
    val isLoading = bankActionState is BankActionState.Loading

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NorthEast,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Virement SEPA vers IBAN",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Disponible : ${IbanValidator.formatEuroFromCents(availableBalanceCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("10", "25", "50", "100", "250").forEach { preset ->
                    FilterChip(
                        selected = amountText == preset,
                        onClick = {
                            amountText = preset
                            onInputChanged()
                        },
                        label = { Text("$preset €") },
                        modifier = Modifier.testTag("withdraw_preset_$preset")
                    )
                }
                if (availableBalanceCents >= IbanValidator.MIN_SEPA_AMOUNT_CENTS) {
                    val maxFormatted = String.format(Locale.US, "%.2f", availableBalanceCents / 100.0)
                    AssistChip(
                        onClick = {
                            amountText = maxFormatted
                            onInputChanged()
                        },
                        label = { Text("Tout retirer") },
                        modifier = Modifier.testTag("withdraw_all_chip")
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { raw ->
                    amountText = raw.filter { it.isDigit() || it == ',' || it == '.' }
                    onInputChanged()
                },
                label = { Text("Montant à retirer en EUR (min. 10,00 €)") },
                suffix = { Text("€") },
                isError = amountText.isNotEmpty() && (!isAmountValid || !hasEnoughBalance),
                supportingText = {
                    when {
                        amountText.isNotEmpty() && !isAmountValid -> {
                            Text("Le montant minimum requis pour un retrait SEPA est de 10,00 €.")
                        }
                        amountText.isNotEmpty() && !hasEnoughBalance -> {
                            Text("Solde disponible insuffisant (${IbanValidator.formatEuroFromCents(availableBalanceCents)}).")
                        }
                        parsedCents != null -> {
                            Text("Virement émis : ${IbanValidator.formatEuroFromCents(parsedCents)}")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("withdraw_amount_input")
            )

            OutlinedTextField(
                value = holderName,
                onValueChange = {
                    holderName = it
                    onInputChanged()
                },
                label = { Text("Bénéficiaire du compte bancaire") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("withdraw_holder_input")
            )

            SepaIbanTextField(
                ibanValue = ibanInput,
                onIbanChange = { updated ->
                    ibanInput = updated
                    onInputChanged()
                },
                label = "IBAN SEPA destinataire (ex: FR76 ...)",
                validationState = ibanValidation,
                supportingTestTag = "withdraw_iban_validation_text",
                modifier = Modifier.testTag("withdraw_iban_input")
            )

            if (isStripeTestMode) {
                AssistChip(
                    onClick = {
                        ibanInput = "DE89370400440532013000"
                        onInputChanged()
                    },
                    label = { Text("Utiliser IBAN Test Stripe (DE89...3000)") },
                    modifier = Modifier.testTag("withdraw_test_iban_chip")
                )
            }

            when (bankActionState) {
                is BankActionState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = bankActionState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is BankActionState.Success -> {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_success_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Virement SEPA Stripe émis",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = bankActionState.message,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "ID : ${bankActionState.tx.stripeId} • Statut : ${bankActionState.tx.statusEnum.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                else -> Unit
            }

            val canSubmit = !isLoading &&
                isAmountValid &&
                hasEnoughBalance &&
                isIbanValid &&
                holderName.trim().length >= 2

            Button(
                onClick = { onSubmitSepaWithdrawal(holderName.trim(), ibanValidation.cleanedIban, amountText) },
                enabled = canSubmit,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_withdraw_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(bankActionState.label)
                } else {
                    Icon(Icons.Rounded.AccountBalance, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (parsedCents != null && isAmountValid) {
                            "Retirer ${IbanValidator.formatEuroFromCents(parsedCents)} vers IBAN"
                        } else {
                            "Confirmer le retrait IBAN (min. 10 €)"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
