package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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

/**
 * Standalone DepositScreen allowing a user to enter an amount (>= 10 EUR) and an IBAN
 * to initiate a real Stripe PaymentIntent for a SEPA Direct Debit transaction.
 */
@Composable
fun DepositScreen(
    walletState: UiState<UserWallet?>,
    bankActionState: BankActionState,
    isStripeConfigured: Boolean,
    isStripeTestMode: Boolean,
    onSubmitSepaDeposit: (holderName: String, iban: String, amountEuro: String) -> Unit,
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
                            modifier = Modifier.testTag("deposit_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Retour"
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Dépôt SEPA Stripe",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Alimentation par IBAN dès 10,00 € via PaymentIntent",
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
                SepaDepositFormCard(
                    initialHolderName = wallet?.displayName.orEmpty(),
                    bankActionState = bankActionState,
                    isStripeTestMode = isStripeTestMode,
                    onSubmitSepaDeposit = onSubmitSepaDeposit,
                    onInputChanged = onClearFeedback,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SepaDepositFormCard(
    initialHolderName: String,
    bankActionState: BankActionState,
    isStripeTestMode: Boolean,
    onSubmitSepaDeposit: (holderName: String, iban: String, amountEuro: String) -> Unit,
    onInputChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var amountText by remember { mutableStateOf("10") }
    var ibanInput by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf(initialHolderName) }
    var mandateAccepted by remember { mutableStateOf(true) }

    LaunchedEffect(initialHolderName) {
        if (holderName.isBlank() && initialHolderName.isNotBlank()) {
            holderName = initialHolderName
        }
    }

    val ibanValidation = remember(ibanInput) { IbanValidator.validateRealtime(ibanInput) }
    val isIbanValid = ibanValidation.isValid
    val parsedCents = IbanValidator.parseEuroToCents(amountText)
    val isAmountValid = parsedCents != null && parsedCents >= IbanValidator.MIN_SEPA_AMOUNT_CENTS
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Prélèvement SEPA par IBAN",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Création d'un PaymentIntent Stripe sécurisé",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        modifier = Modifier.testTag("deposit_preset_$preset")
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { raw ->
                    amountText = raw.filter { it.isDigit() || it == ',' || it == '.' }
                    onInputChanged()
                },
                label = { Text("Montant du dépôt en EUR (min. 10,00 €)") },
                suffix = { Text("€") },
                isError = amountText.isNotEmpty() && !isAmountValid,
                supportingText = {
                    if (amountText.isNotEmpty() && !isAmountValid) {
                        Text("Le montant minimum requis pour un dépôt SEPA est de 10,00 €.")
                    } else if (parsedCents != null) {
                        Text("Montant débité : ${IbanValidator.formatEuroFromCents(parsedCents)}")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deposit_amount_input")
            )

            OutlinedTextField(
                value = holderName,
                onValueChange = {
                    holderName = it
                    onInputChanged()
                },
                label = { Text("Titulaire du compte bancaire") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deposit_holder_input")
            )

            SepaIbanTextField(
                ibanValue = ibanInput,
                onIbanChange = { updated ->
                    ibanInput = updated
                    onInputChanged()
                },
                label = "IBAN SEPA (ex: FR76 ...)",
                validationState = ibanValidation,
                supportingTestTag = "deposit_iban_validation_text",
                modifier = Modifier.testTag("deposit_iban_input")
            )

            if (isStripeTestMode) {
                AssistChip(
                    onClick = {
                        ibanInput = "DE89370400440532013000"
                        onInputChanged()
                    },
                    label = { Text("Utiliser IBAN Test Stripe (DE89...3000)") },
                    modifier = Modifier.testTag("deposit_test_iban_chip")
                )
            }

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { mandateAccepted = !mandateAccepted }
            ) {
                Checkbox(
                    checked = mandateAccepted,
                    onCheckedChange = { mandateAccepted = it },
                    modifier = Modifier.testTag("deposit_mandate_checkbox")
                )
                Text(
                    text = "En autorisant ce prélèvement SEPA, j'accepte le mandat Stripe permettant de débiter mon compte bancaire du montant saisi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            when (bankActionState) {
                is BankActionState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("deposit_error_banner")
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
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("deposit_success_banner")
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
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "PaymentIntent Stripe créé",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = bankActionState.message,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "ID : ${bankActionState.tx.stripeId} • Statut : ${bankActionState.tx.statusEnum.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                else -> Unit
            }

            val canSubmit = !isLoading &&
                isAmountValid &&
                isIbanValid &&
                holderName.trim().length >= 2 &&
                mandateAccepted

            Button(
                onClick = {
                    onSubmitSepaDeposit(holderName.trim(), ibanValidation.cleanedIban, amountText)
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_deposit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(bankActionState.label)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (parsedCents != null && isAmountValid) {
                            "Déposer ${IbanValidator.formatEuroFromCents(parsedCents)} par SEPA"
                        } else {
                            "Confirmer le dépôt SEPA (min. 10 €)"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
