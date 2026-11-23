package com.example.data.stripe

import com.example.BuildConfig
import com.example.util.IbanValidator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Retrofit service contract for Stripe REST API v1 endpoints used by KairoIA SEPA flows.
 */
interface StripeApiService {
    @FormUrlEncoded
    @POST("v1/customers")
    suspend fun createCustomer(
        @Field("email") email: String,
        @Field("name") name: String,
        @Field("metadata[firebase_uid]") firebaseUid: String,
    ): StripeCustomerResponse

    @FormUrlEncoded
    @POST("v1/payment_methods")
    suspend fun createSepaPaymentMethod(
        @Field("type") type: String = "sepa_debit",
        @Field("sepa_debit[iban]") iban: String,
        @Field("billing_details[name]") holderName: String,
        @Field("billing_details[email]") email: String,
    ): StripePaymentMethodResponse

    @FormUrlEncoded
    @POST("v1/payment_intents")
    suspend fun createPaymentIntent(
        @FieldMap fields: Map<String, String>,
    ): StripePaymentIntent

    @GET("v1/payment_intents/{id}")
    suspend fun retrievePaymentIntent(
        @Path("id") paymentIntentId: String,
    ): StripePaymentIntent

    @FormUrlEncoded
    @POST("v1/tokens")
    suspend fun createBankAccountToken(
        @Field("bank_account[country]") country: String,
        @Field("bank_account[currency]") currency: String = "eur",
        @Field("bank_account[account_holder_name]") holderName: String,
        @Field("bank_account[account_holder_type]") holderType: String = "individual",
        @Field("bank_account[account_number]") iban: String,
    ): StripeTokenResponse

    @FormUrlEncoded
    @POST("v1/payouts")
    suspend fun createSepaPayout(
        @FieldMap fields: Map<String, String>,
    ): StripePayoutResponse

    @GET("v1/payouts/{id}")
    suspend fun retrievePayout(
        @Path("id") payoutId: String,
    ): StripePayoutResponse
}

/**
 * Retrofit builder class that configures OkHttp authentication headers and Moshi adapters for Stripe.
 */
class StripeServiceClient(
    private val secretKeyProvider: () -> String = { BuildConfig.STRIPE_SECRET_KEY.trim() },
    private val baseUrl: String = "https://api.stripe.com/",
) {
    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val api: StripeApiService by lazy {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${secretKeyProvider()}")
                .addHeader("Stripe-Version", "2024-06-20")
                .build()
            chain.proceed(request)
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(StripeApiService::class.java)
    }
}

class StripeRepository(
    private val serviceClient: StripeServiceClient = StripeServiceClient(),
) {
    private val secretKey: String
        get() = BuildConfig.STRIPE_SECRET_KEY.trim()

    private val publishableKey: String
        get() = BuildConfig.STRIPE_PUBLISHABLE_KEY.trim()

    fun isStripeConfigured(): Boolean =
        secretKey.isNotEmpty() &&
            secretKey != "MY_STRIPE_SECRET_KEY" &&
            (secretKey.startsWith("sk_test_") || secretKey.startsWith("sk_live_") || secretKey.startsWith("rk_"))

    fun isTestMode(): Boolean = secretKey.startsWith("sk_test_") || publishableKey.startsWith("pk_test_")

    suspend fun createSepaDeposit(
        userId: String,
        existingCustomerId: String?,
        email: String,
        holderName: String,
        iban: String,
        amountCents: Long,
    ): Result<StripeDepositResult> = withContext(Dispatchers.IO) {
        try {
            require(isStripeConfigured()) {
                "Clé secrète Stripe manquante. Configurez STRIPE_SECRET_KEY dans le panneau Secrets d'AI Studio."
            }
            require(amountCents >= IbanValidator.MIN_SEPA_AMOUNT_CENTS) {
                "Le montant minimum de dépôt est de 10,00 €."
            }
            val cleanedIban = IbanValidator.cleanIban(iban)
            require(IbanValidator.isValidSepaIban(cleanedIban)) {
                "IBAN SEPA invalide. Vérifiez les caractères saisis."
            }

            val customerId = existingCustomerId?.takeIf { it.startsWith("cus_") }
                ?: serviceClient.api.createCustomer(
                    email = email,
                    name = holderName,
                    firebaseUid = userId,
                ).id

            val paymentMethod = serviceClient.api.createSepaPaymentMethod(
                iban = cleanedIban,
                holderName = holderName,
                email = email,
            )

            val paymentIntentRequest = SepaPaymentIntentRequest(
                amountCents = amountCents,
                customerId = customerId,
                paymentMethodId = paymentMethod.id,
                firebaseUid = userId,
            )
            val paymentIntent = serviceClient.api.createPaymentIntent(paymentIntentRequest.toFieldMap())

            Result.success(
                StripeDepositResult(
                    customerId = customerId,
                    paymentIntentId = paymentIntent.id,
                    status = paymentIntent.status,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(parseStripeError(e)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPaymentIntentStatus(paymentIntentId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            require(isStripeConfigured()) {
                "Clé secrète Stripe manquante dans les Secrets d'AI Studio."
            }
            val pi = serviceClient.api.retrievePaymentIntent(paymentIntentId)
            Result.success(pi.status)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(parseStripeError(e)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSepaWithdrawal(
        userId: String,
        holderName: String,
        iban: String,
        amountCents: Long,
    ): Result<StripeWithdrawalResult> = withContext(Dispatchers.IO) {
        try {
            require(isStripeConfigured()) {
                "Clé secrète Stripe manquante. Configurez STRIPE_SECRET_KEY dans le panneau Secrets d'AI Studio."
            }
            require(amountCents >= IbanValidator.MIN_SEPA_AMOUNT_CENTS) {
                "Le montant minimum de retrait est de 10,00 €."
            }
            val cleanedIban = IbanValidator.cleanIban(iban)
            require(IbanValidator.isValidSepaIban(cleanedIban)) {
                "IBAN SEPA invalide. Vérifiez les caractères saisis."
            }

            val country = IbanValidator.extractCountryCode(cleanedIban)
            val bankToken = serviceClient.api.createBankAccountToken(
                country = country,
                holderName = holderName,
                iban = cleanedIban,
            )

            val payoutRequest = SepaPayoutRequest(
                amountCents = amountCents,
                firebaseUid = userId,
                ibanMasked = IbanValidator.maskIban(cleanedIban),
                bankTokenId = bankToken.id,
            )
            val payout = serviceClient.api.createSepaPayout(payoutRequest.toFieldMap())

            Result.success(
                StripeWithdrawalResult(
                    payoutId = payout.id,
                    status = payout.status,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(parseStripeError(e)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPayoutStatus(payoutId: String): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
        try {
            require(isStripeConfigured()) {
                "Clé secrète Stripe manquante dans les Secrets d'AI Studio."
            }
            val payout = serviceClient.api.retrievePayout(payoutId)
            Result.success(payout.status to payout.failureMessage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Result.failure(IllegalStateException(parseStripeError(e)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseStripeError(e: HttpException): String {
        val rawBody = try {
            e.response()?.errorBody()?.string().orEmpty()
        } catch (_: Exception) {
            ""
        }
        return try {
            val json = JSONObject(rawBody)
            val err = json.optJSONObject("error")
            val msg = err?.optString("message")?.takeIf { it.isNotBlank() }
            msg ?: "Erreur Stripe (HTTP ${e.code()})"
        } catch (_: Exception) {
            "Erreur Stripe (HTTP ${e.code()})"
        }
    }
}
