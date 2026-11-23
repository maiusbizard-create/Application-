package com.example.data.stripe

import androidx.annotation.Keep
import com.example.util.IbanValidator
import com.squareup.moshi.Json

/**
 * Typed request model for creating and confirming a Stripe SEPA Direct Debit PaymentIntent.
 * Serializes to form-urlencoded fields expected by `POST /v1/payment_intents`.
 */
data class SepaPaymentIntentRequest(
    val amountCents: Long,
    val currency: String = "eur",
    val customerId: String,
    val paymentMethodId: String,
    val firebaseUid: String,
    val confirm: Boolean = true,
    val description: String = "Dépôt KairoIA SEPA IBAN",
    val acceptanceType: String = "online",
    val ipAddress: String = "127.0.0.1",
    val userAgent: String = "KairoIA-Android/1.0",
) {
    init {
        require(amountCents >= IbanValidator.MIN_SEPA_AMOUNT_CENTS) {
            "Le montant minimum SEPA est de 10,00 € (1000 centimes)."
        }
        require(currency.equals("eur", ignoreCase = true)) {
            "La devise SEPA doit être EUR."
        }
        require(customerId.isNotBlank()) { "customerId requis." }
        require(paymentMethodId.isNotBlank()) { "paymentMethodId requis." }
    }

    fun toFieldMap(): Map<String, String> = mapOf(
        "amount" to amountCents.toString(),
        "currency" to currency.lowercase(),
        "customer" to customerId,
        "payment_method" to paymentMethodId,
        "payment_method_types[0]" to "sepa_debit",
        "confirm" to confirm.toString(),
        "mandate_data[customer_acceptance][type]" to acceptanceType,
        "mandate_data[customer_acceptance][online][ip_address]" to ipAddress,
        "mandate_data[customer_acceptance][online][user_agent]" to userAgent,
        "description" to description,
        "metadata[firebase_uid]" to firebaseUid,
    )
}

@Keep
data class StripeLastPaymentError(
    val code: String? = null,
    val message: String? = null,
    val type: String? = null,
)

@Keep
data class StripePaymentIntent(
    val id: String,
    val status: String,
    val amount: Long = 0L,
    val currency: String = "eur",
    @Json(name = "client_secret") val clientSecret: String? = null,
    val customer: String? = null,
    @Json(name = "payment_method") val paymentMethod: String? = null,
    @Json(name = "latest_charge") val latestCharge: String? = null,
    @Json(name = "last_payment_error") val lastPaymentError: StripeLastPaymentError? = null,
    val created: Long = 0L,
)

@Keep
data class StripeCustomerResponse(
    val id: String,
    val email: String? = null,
)

@Keep
data class StripePaymentMethodResponse(
    val id: String,
    val type: String? = null,
)

@Keep
data class StripeTokenResponse(
    val id: String,
    val type: String? = null,
)

@Keep
data class StripePayoutResponse(
    val id: String,
    val status: String,
    val amount: Long = 0L,
    val currency: String = "eur",
    @Json(name = "failure_message") val failureMessage: String? = null,
)

/**
 * Typed request model for creating a Stripe SEPA Payout to a validated IBAN bank account.
 */
data class SepaPayoutRequest(
    val amountCents: Long,
    val currency: String = "eur",
    val method: String = "standard",
    val description: String = "Retrait KairoIA SEPA IBAN",
    val firebaseUid: String,
    val ibanMasked: String,
    val bankTokenId: String,
) {
    init {
        require(amountCents >= IbanValidator.MIN_SEPA_AMOUNT_CENTS) {
            "Le montant minimum de retrait SEPA est de 10,00 € (1000 centimes)."
        }
        require(currency.equals("eur", ignoreCase = true)) {
            "La devise de retrait SEPA doit être EUR."
        }
        require(bankTokenId.isNotBlank()) { "bankTokenId requis." }
    }

    fun toFieldMap(): Map<String, String> = mapOf(
        "amount" to amountCents.toString(),
        "currency" to currency.lowercase(),
        "method" to method,
        "description" to description,
        "metadata[firebase_uid]" to firebaseUid,
        "metadata[iban_masked]" to ibanMasked,
        "metadata[bank_token_id]" to bankTokenId,
    )
}

data class StripeDepositResult(
    val customerId: String,
    val paymentIntentId: String,
    val status: String,
)

data class StripeWithdrawalResult(
    val payoutId: String,
    val status: String,
)
