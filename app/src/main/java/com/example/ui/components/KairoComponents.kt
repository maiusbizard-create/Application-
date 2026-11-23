package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SepaTransaction
import com.example.data.model.TxStatus
import com.example.data.model.TxType
import com.example.util.IbanValidationState
import com.example.util.IbanValidator
import com.example.util.IbanVisualTransformation
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun StripeConfigurationBanner(
    isConfigured: Boolean,
    isTestMode: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isConfigured) {
        Surface(
            color = if (isTestMode) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            },
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isTestMode) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = if (isTestMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isTestMode) {
                        "Stripe SEPA connecté (Mode Test sk_test_...)"
                    } else {
                        "Stripe SEPA connecté (Mode Production Live)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
            shape = RoundedCornerShape(18.dp),
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = "Configuration Stripe requise",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Clé API Stripe requise pour les flux réels",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Ajoutez STRIPE_SECRET_KEY (sk_test_... ou sk_live_...) et STRIPE_PUBLISHABLE_KEY dans le panneau Secrets d'AI Studio pour exécuter de vrais dépôts et retraits SEPA IBAN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionRowCard(
    tx: SepaTransaction,
    isRefreshing: Boolean,
    onRefreshStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDeposit = tx.typeEnum == TxType.DEPOSIT
    val statusColor = when (tx.statusEnum) {
        TxStatus.SUCCEEDED -> MaterialTheme.colorScheme.primary
        TxStatus.PROCESSING -> MaterialTheme.colorScheme.tertiary
        TxStatus.FAILED, TxStatus.CANCELED -> MaterialTheme.colorScheme.error
    }
    val dateLabel = tx.createdAt?.toDate()?.let { date ->
        SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.FRANCE).format(date)
    } ?: "En cours d'horodatage..."

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDeposit) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDeposit) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                    contentDescription = tx.typeEnum.label,
                    tint = if (isDeposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "${tx.typeEnum.label} • ${tx.ibanMasked}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = tx.statusEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = tx.stripeId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isDeposit) "+" else "-") + IbanValidator.formatEuroFromCents(tx.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDeposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (tx.statusEnum == TxStatus.PROCESSING) {
                    Spacer(Modifier.height(4.dp))
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = onRefreshStatus,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("refresh_tx_${tx.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Synchroniser statut Stripe",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GradientWalletHeader(
    balanceCents: Long,
    pendingCents: Long,
    botActive: Boolean,
    riskLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0E2926),
                        Color(0xFF101C33),
                        Color(0xFF162238),
                    )
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(26.dp))
            .padding(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SOLDE DISPONIBLE (EUR)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
                Surface(
                    color = if (botActive) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (botActive) Color(0xFF34D399) else Color(0xFFFBBF24))
                        )
                        Text(
                            text = if (botActive) "Robot IA Actif • $riskLabel" else "Robot IA En pause",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (botActive) Color(0xFF6EE7B7) else Color(0xFFFDE68A)
                        )
                    }
                }
            }

            Text(
                text = IbanValidator.formatEuroFromCents(balanceCents),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "En cours SEPA : ${IbanValidator.formatEuroFromCents(pendingCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
                Text(
                    text = "Min. 10,00 €",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF38BDF8)
                )
            }
        }
    }
}

@Composable
fun SepaIbanTextField(
    ibanValue: String,
    onIbanChange: (String) -> Unit,
    label: String,
    validationState: IbanValidationState,
    modifier: Modifier = Modifier,
    supportingTestTag: String? = null,
) {
    val visualTransformation = remember { IbanVisualTransformation() }

    val supportingMessage = when (validationState) {
        is IbanValidationState.Empty ->
            "Format SEPA : code pays (2 lettres) + clé (2 chiffres) + BBAN"
        is IbanValidationState.InvalidCharacters ->
            "Caractères non autorisés : utilisez uniquement des lettres (A-Z) et chiffres (0-9)."
        is IbanValidationState.InvalidPrefix ->
            validationState.message
        is IbanValidationState.IncompleteLength -> {
            val expected = validationState.expectedLength
            val country = validationState.countryName
            when {
                country != null && expected != null ->
                    "$country (${validationState.countryCode}) : ${validationState.currentLength}/$expected caractères"
                expected != null ->
                    "Saisie en cours (${validationState.countryCode}) : ${validationState.currentLength}/$expected car. min."
                else ->
                    "Code pays en cours : ${validationState.currentLength} caractère"
            }
        }
        is IbanValidationState.InvalidLengthExceeded ->
            "Longueur dépassée (${validationState.countryName ?: validationState.countryCode}) : ${validationState.currentLength}/${validationState.expectedLength} car. max."
        is IbanValidationState.InvalidFormat ->
            validationState.message
        is IbanValidationState.InvalidChecksum ->
            "Clé de contrôle IBAN invalide (échec vérification ISO 13616 MOD-97)."
        is IbanValidationState.Valid ->
            "IBAN ${validationState.countryName ?: validationState.countryCode} valide (${validationState.currentLength}/${validationState.expectedLength} car.) • MOD-97 vérifié"
    }

    val supportingColor = when {
        validationState.isValid -> MaterialTheme.colorScheme.primary
        validationState.isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    OutlinedTextField(
        value = ibanValue,
        onValueChange = { raw ->
            onIbanChange(IbanValidator.cleanIban(raw).take(38))
        },
        visualTransformation = visualTransformation,
        label = { Text(label) },
        placeholder = { Text("FR76 3000 6000 0112 3456 7890 189") },
        isError = validationState.isError,
        trailingIcon = {
            when (validationState) {
                is IbanValidationState.Valid -> Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "IBAN valide",
                    tint = MaterialTheme.colorScheme.primary
                )
                is IbanValidationState.IncompleteLength -> {
                    val counter = validationState.expectedLength?.let {
                        "${validationState.currentLength}/$it"
                    } ?: "${validationState.currentLength}"
                    Text(
                        text = counter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                else -> if (validationState.isError) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "IBAN invalide",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        supportingText = {
            Text(
                text = supportingMessage,
                color = supportingColor,
                modifier = if (supportingTestTag != null) Modifier.testTag(supportingTestTag) else Modifier
            )
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii
        ),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    )
}
