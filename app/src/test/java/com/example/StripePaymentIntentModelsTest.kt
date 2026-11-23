package com.example

import com.example.data.stripe.SepaPaymentIntentRequest
import com.example.data.stripe.StripePaymentIntent
import com.example.data.stripe.StripeServiceClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StripePaymentIntentModelsTest {
    @Test
    fun sepaPaymentIntentRequestBuildsValidFieldMap() {
        val req = SepaPaymentIntentRequest(
            amountCents = 1500L,
            customerId = "cus_123",
            paymentMethodId = "pm_456",
            firebaseUid = "uid_789",
        )
        val fields = req.toFieldMap()
        assertEquals("1500", fields["amount"])
        assertEquals("eur", fields["currency"])
        assertEquals("cus_123", fields["customer"])
        assertEquals("pm_456", fields["payment_method"])
        assertEquals("sepa_debit", fields["payment_method_types[0]"])
        assertEquals("true", fields["confirm"])
        assertEquals("online", fields["mandate_data[customer_acceptance][type]"])
        assertEquals("uid_789", fields["metadata[firebase_uid]"])
    }

    @Test
    fun sepaPaymentIntentRequestRejectsAmountBelowTenEuros() {
        assertThrows(IllegalArgumentException::class.java) {
            SepaPaymentIntentRequest(
                amountCents = 999L,
                customerId = "cus_123",
                paymentMethodId = "pm_456",
                firebaseUid = "uid_789",
            )
        }
    }

    @Test
    fun moshiParsesStripePaymentIntentJson() {
        val json = """
            {
              "id": "pi_3Qx123",
              "status": "processing",
              "amount": 2500,
              "currency": "eur",
              "client_secret": "pi_3Qx123_secret_abc",
              "customer": "cus_123",
              "payment_method": "pm_456",
              "latest_charge": "ch_789",
              "created": 1735000000
            }
        """.trimIndent()
        val adapter = StripeServiceClient().moshi.adapter(StripePaymentIntent::class.java)
        val parsed = adapter.fromJson(json)!!
        assertEquals("pi_3Qx123", parsed.id)
        assertEquals("processing", parsed.status)
        assertEquals(2500L, parsed.amount)
        assertEquals("pi_3Qx123_secret_abc", parsed.clientSecret)
        assertEquals("ch_789", parsed.latestCharge)
    }

    @Test
    fun sepaPayoutRequestBuildsValidFieldMapAndEnforcesMinAmount() {
        val payoutReq = com.example.data.stripe.SepaPayoutRequest(
            amountCents = 2500L,
            firebaseUid = "uid_789",
            ibanMasked = "DE89 •••• •••• 3000",
            bankTokenId = "btok_123",
        )
        val fields = payoutReq.toFieldMap()
        assertEquals("2500", fields["amount"])
        assertEquals("eur", fields["currency"])
        assertEquals("standard", fields["method"])
        assertEquals("btok_123", fields["metadata[bank_token_id]"])

        assertThrows(IllegalArgumentException::class.java) {
            com.example.data.stripe.SepaPayoutRequest(
                amountCents = 500L,
                firebaseUid = "uid_789",
                ibanMasked = "DE89 •••• •••• 3000",
                bankTokenId = "btok_123",
            )
        }
    }
}
