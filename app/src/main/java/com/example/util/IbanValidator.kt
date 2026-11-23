package com.example.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

data class IbanCountrySpec(
    val code: String,
    val countryNameFr: String,
    val expectedLength: Int,
    val formatRegex: Regex,
    val formatHint: String,
)

sealed interface IbanValidationState {
    val cleanedIban: String
    val currentLength: Int
    val expectedLength: Int?
    val countryName: String?

    val isValid: Boolean get() = this is Valid
    val isError: Boolean
        get() = this is InvalidCharacters ||
            this is InvalidPrefix ||
            this is InvalidLengthExceeded ||
            this is InvalidFormat ||
            this is InvalidChecksum

    data object Empty : IbanValidationState {
        override val cleanedIban: String = ""
        override val currentLength: Int = 0
        override val expectedLength: Int? = null
        override val countryName: String? = null
    }

    data class InvalidCharacters(
        override val cleanedIban: String,
        override val currentLength: Int,
    ) : IbanValidationState {
        override val expectedLength: Int? = null
        override val countryName: String? = null
    }

    data class InvalidPrefix(
        override val cleanedIban: String,
        override val currentLength: Int,
        override val expectedLength: Int?,
        override val countryName: String?,
        val message: String,
    ) : IbanValidationState

    data class IncompleteLength(
        override val cleanedIban: String,
        override val currentLength: Int,
        override val expectedLength: Int?,
        override val countryName: String?,
        val countryCode: String,
    ) : IbanValidationState

    data class InvalidLengthExceeded(
        override val cleanedIban: String,
        override val currentLength: Int,
        override val expectedLength: Int,
        override val countryName: String?,
        val countryCode: String,
    ) : IbanValidationState

    data class InvalidFormat(
        override val cleanedIban: String,
        override val currentLength: Int,
        override val expectedLength: Int?,
        override val countryName: String?,
        val message: String,
    ) : IbanValidationState

    data class InvalidChecksum(
        override val cleanedIban: String,
        override val currentLength: Int,
        override val expectedLength: Int?,
        override val countryName: String?,
        val countryCode: String,
    ) : IbanValidationState

    data class Valid(
        override val cleanedIban: String,
        override val currentLength: Int,
        override val expectedLength: Int,
        override val countryName: String?,
        val countryCode: String,
    ) : IbanValidationState
}

/**
 * Formats an unspaced IBAN string into groups of 4 characters while preserving cursor position.
 */
class IbanVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = buildString {
            raw.forEachIndexed { index, ch ->
                if (index > 0 && index % 4 == 0) append(' ')
                append(ch)
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, raw.length)
                if (clamped == 0) return 0
                val spacesBefore = (clamped - 1) / 4
                return (clamped + spacesBefore).coerceAtMost(formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, formatted.length)
                val spacesBefore = clamped / 5
                return (clamped - spacesBefore).coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

object IbanValidator {
    const val MIN_SEPA_AMOUNT_CENTS: Long = 1000L // 10,00 €
    const val MAX_SEPA_AMOUNT_CENTS: Long = 100_000_000L // 1 000 000,00 €
    const val MIN_IBAN_LENGTH = 15
    const val MAX_IBAN_LENGTH = 34

    private val genericIbanPattern = Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$")
    private val allowedCharsPattern = Regex("^[A-Z0-9]*$")

    private val countrySpecs: Map<String, IbanCountrySpec> = listOf(
        IbanCountrySpec("FR", "France", 27, Regex("^FR[0-9]{12}[A-Z0-9]{11}[0-9]{2}$"), "FR + 2 chiffres + BBAN 23 car."),
        IbanCountrySpec("DE", "Allemagne", 22, Regex("^DE[0-9]{20}$"), "DE + 20 chiffres"),
        IbanCountrySpec("BE", "Belgique", 16, Regex("^BE[0-9]{14}$"), "BE + 14 chiffres"),
        IbanCountrySpec("ES", "Espagne", 24, Regex("^ES[0-9]{22}$"), "ES + 22 chiffres"),
        IbanCountrySpec("IT", "Italie", 27, Regex("^IT[0-9]{2}[A-Z][0-9]{10}[A-Z0-9]{12}$"), "IT + 2 chiffres + 1 lettre + 22 car."),
        IbanCountrySpec("NL", "Pays-Bas", 18, Regex("^NL[0-9]{2}[A-Z]{4}[0-9]{10}$"), "NL + 2 chiffres + 4 lettres + 10 chiffres"),
        IbanCountrySpec("PT", "Portugal", 25, Regex("^PT[0-9]{23}$"), "PT + 23 chiffres"),
        IbanCountrySpec("LU", "Luxembourg", 20, Regex("^LU[0-9]{5}[A-Z0-9]{13}$"), "LU + 5 chiffres + 13 car."),
        IbanCountrySpec("AT", "Autriche", 20, Regex("^AT[0-9]{18}$"), "AT + 18 chiffres"),
        IbanCountrySpec("CH", "Suisse", 21, Regex("^CH[0-9]{7}[A-Z0-9]{12}$"), "CH + 7 chiffres + 12 car."),
        IbanCountrySpec("IE", "Irlande", 22, Regex("^IE[0-9]{2}[A-Z]{4}[0-9]{14}$"), "IE + 2 chiffres + 4 lettres + 14 chiffres"),
        IbanCountrySpec("GB", "Royaume-Uni", 22, Regex("^GB[0-9]{2}[A-Z]{4}[0-9]{14}$"), "GB + 2 chiffres + 4 lettres + 14 chiffres"),
        IbanCountrySpec("MC", "Monaco", 27, Regex("^MC[0-9]{12}[A-Z0-9]{11}[0-9]{2}$"), "MC + 12 chiffres + 11 car. + 2 chiffres"),
        IbanCountrySpec("FI", "Finlande", 18, Regex("^FI[0-9]{16}$"), "FI + 16 chiffres"),
        IbanCountrySpec("GR", "Grèce", 27, Regex("^GR[0-9]{9}[A-Z0-9]{16}$"), "GR + 9 chiffres + 16 car."),
        IbanCountrySpec("PL", "Pologne", 28, Regex("^PL[0-9]{26}$"), "PL + 26 chiffres"),
    ).associateBy { it.code }

    fun cleanIban(raw: String): String =
        raw.replace("\\s+".toRegex(), "").replace("-", "").uppercase(Locale.ROOT)

    fun sanitizeInput(raw: String): String {
        val cleaned = cleanIban(raw).filter { it in 'A'..'Z' || it in '0'..'9' }
        val maxLen = if (cleaned.length >= 2) {
            countrySpecs[cleaned.take(2)]?.expectedLength ?: MAX_IBAN_LENGTH
        } else {
            MAX_IBAN_LENGTH
        }
        return cleaned.take(maxLen)
    }

    fun formatIbanDisplay(raw: String): String {
        val cleaned = cleanIban(raw)
        return cleaned.chunked(4).joinToString(" ")
    }

    fun maskIban(raw: String): String {
        val cleaned = cleanIban(raw)
        if (cleaned.length < 8) return "IBAN ••••"
        val prefix = cleaned.take(4)
        val suffix = cleaned.takeLast(4)
        return "$prefix •••• •••• $suffix"
    }

    fun extractCountryCode(raw: String): String {
        val cleaned = cleanIban(raw)
        return if (cleaned.length >= 2) cleaned.take(2) else "FR"
    }

    fun getCountrySpec(countryCode: String): IbanCountrySpec? = countrySpecs[countryCode.uppercase(Locale.ROOT)]

    /**
     * Real-time IBAN validation returning incremental length, country format, and MOD-97 checksum status.
     */
    fun validateRealtime(raw: String): IbanValidationState {
        val cleaned = cleanIban(raw)
        if (cleaned.isEmpty()) return IbanValidationState.Empty

        if (!allowedCharsPattern.matches(cleaned)) {
            return IbanValidationState.InvalidCharacters(
                cleanedIban = cleaned,
                currentLength = cleaned.length,
            )
        }

        val countryPrefix = cleaned.take(2)
        val spec = if (cleaned.length >= 2 && countryPrefix.all { it in 'A'..'Z' }) {
            countrySpecs[countryPrefix]
        } else {
            null
        }

        // 1. Validate 2-letter country prefix
        val prefixPart = cleaned.take(2)
        if (!prefixPart.all { it in 'A'..'Z' }) {
            return IbanValidationState.InvalidPrefix(
                cleanedIban = cleaned,
                currentLength = cleaned.length,
                expectedLength = spec?.expectedLength,
                countryName = spec?.countryNameFr,
                message = "Les 2 premiers caractères doivent correspondre au code pays (ex: FR, DE, BE).",
            )
        }

        if (cleaned.length < 2) {
            return IbanValidationState.IncompleteLength(
                cleanedIban = cleaned,
                currentLength = cleaned.length,
                expectedLength = null,
                countryName = null,
                countryCode = cleaned,
            )
        }

        // 2. Validate 2-digit check digits (positions 2 and 3)
        if (cleaned.length >= 3) {
            val checkDigits = cleaned.substring(2, minOf(4, cleaned.length))
            if (!checkDigits.all { it in '0'..'9' }) {
                return IbanValidationState.InvalidPrefix(
                    cleanedIban = cleaned,
                    currentLength = cleaned.length,
                    expectedLength = spec?.expectedLength,
                    countryName = spec?.countryNameFr,
                    message = "Les caractères 3 et 4 doivent être des chiffres de contrôle (ex: FR76).",
                )
            }
        }

        // 3. Length checks (country-specific if known, otherwise ISO 15..34)
        if (spec != null) {
            if (cleaned.length > spec.expectedLength) {
                return IbanValidationState.InvalidLengthExceeded(
                    cleanedIban = cleaned,
                    currentLength = cleaned.length,
                    expectedLength = spec.expectedLength,
                    countryName = spec.countryNameFr,
                    countryCode = spec.code,
                )
            }
            if (cleaned.length < spec.expectedLength) {
                return IbanValidationState.IncompleteLength(
                    cleanedIban = cleaned,
                    currentLength = cleaned.length,
                    expectedLength = spec.expectedLength,
                    countryName = spec.countryNameFr,
                    countryCode = spec.code,
                )
            }
            // Exact country length reached: check country-specific BBAN format
            if (!spec.formatRegex.matches(cleaned)) {
                return IbanValidationState.InvalidFormat(
                    cleanedIban = cleaned,
                    currentLength = cleaned.length,
                    expectedLength = spec.expectedLength,
                    countryName = spec.countryNameFr,
                    message = "Format ${spec.countryNameFr} invalide (attendu : ${spec.formatHint}).",
                )
            }
        } else {
            if (cleaned.length > MAX_IBAN_LENGTH) {
                return IbanValidationState.InvalidLengthExceeded(
                    cleanedIban = cleaned,
                    currentLength = cleaned.length,
                    expectedLength = MAX_IBAN_LENGTH,
                    countryName = null,
                    countryCode = countryPrefix,
                )
            }
            if (cleaned.length < MIN_IBAN_LENGTH) {
                return IbanValidationState.IncompleteLength(
                    cleanedIban = cleaned,
                    currentLength = cleaned.length,
                    expectedLength = MIN_IBAN_LENGTH,
                    countryName = null,
                    countryCode = countryPrefix,
                )
            }
            if (!genericIbanPattern.matches(cleaned)) {
                return IbanValidationState.InvalidFormat(
                    cleanedIban = cleaned,
                    currentLength = cleaned.length,
                    expectedLength = null,
                    countryName = null,
                    message = "Format IBAN invalide (code pays + 2 chiffres + BBAN alphanumérique).",
                )
            }
        }

        // 4. ISO 13616 MOD 97-10 checksum verification
        if (!passesMod97(cleaned)) {
            return IbanValidationState.InvalidChecksum(
                cleanedIban = cleaned,
                currentLength = cleaned.length,
                expectedLength = spec?.expectedLength ?: cleaned.length,
                countryName = spec?.countryNameFr,
                countryCode = countryPrefix,
            )
        }

        return IbanValidationState.Valid(
            cleanedIban = cleaned,
            currentLength = cleaned.length,
            expectedLength = spec?.expectedLength ?: cleaned.length,
            countryName = spec?.countryNameFr,
            countryCode = countryPrefix,
        )
    }

    private fun passesMod97(cleaned: String): Boolean {
        val rearranged = cleaned.drop(4) + cleaned.take(4)
        val numericString = buildString(rearranged.length * 2) {
            for (ch in rearranged) {
                when (ch) {
                    in '0'..'9' -> append(ch)
                    in 'A'..'Z' -> append((ch - 'A') + 10)
                    else -> return false
                }
            }
        }
        return BigInteger(numericString).mod(BigInteger.valueOf(97L)) == BigInteger.ONE
    }

    /**
     * Validates an IBAN using country-specific SEPA length/format and ISO 13616 MOD 97-10 checksum.
     */
    fun isValidSepaIban(raw: String): Boolean = validateRealtime(raw).isValid

    fun parseEuroToCents(input: String): Long? {
        val normalized = input.trim().replace(" ", "").replace(",", ".")
        if (normalized.isEmpty()) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        return try {
            val cents = value.movePointRight(2).longValueExact()
            if (cents > 0L) cents else null
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun formatEuroFromCents(cents: Long): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.FRANCE)
        return formatter.format(cents / 100.0)
    }
}
