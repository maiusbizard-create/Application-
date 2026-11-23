package com.example.data.repository

import android.util.Log
import com.example.data.model.AssetAllocationItem
import com.example.data.model.RobotStrategyPlan
import com.example.data.model.SepaTransaction
import com.example.data.model.UserWallet
import com.example.util.IbanValidator
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "AiRobotRepository"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val AI_TIMEOUT_MS = 60_000L

class AiRobotRepository {

    private val planSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Synthèse claire et concise en français de la stratégie du robot IA"),
            "recommendedAction" to Schema.enumeration(
                values = listOf("INITIALISER", "RENFORCER", "RÉÉQUILIBRER", "CONSERVER"),
                description = "Action prioritaire recommandée"
            ),
            "riskScore" to Schema.integer(description = "Score de risque global de 1 (très sûr) à 10 (agressif)"),
            "projectedAnnualYieldPercent" to Schema.double(description = "Rendement annuel estimé en pourcentage, ex: 7.2"),
            "allocations" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "assetName" to Schema.string(description = "Nom de la classe d'actifs ou ETF"),
                        "percentage" to Schema.integer(description = "Pourcentage alloué (0 à 100)"),
                        "rationale" to Schema.string(description = "Justification courte en français")
                    )
                ),
                description = "3 à 4 lignes d'allocation totalisant 100%"
            ),
            "nextSteps" to Schema.array(
                items = Schema.string(description = "Conseil pratique court en français"),
                description = "2 à 3 prochaines étapes concrètes"
            )
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = planSchema
            },
            systemInstruction = content {
                text(
                    "Tu es KairoBot, le robot IA d'allocation patrimoniale de l'application KairoIA. " +
                        "Réponds toujours en français professionnel, précis et pédagogique. " +
                        "Rappelle quand pertinent que les dépôts et retraits SEPA par IBAN via Stripe sont accessibles dès 10,00 €. " +
                        "Respecte strictement le profil de risque de l'utilisateur et ne promets jamais de gain garanti."
                )
            }
        )
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(
                    "Tu es KairoBot, l'assistant robot IA financier de KairoIA. " +
                        "Tu aides l'utilisateur en français à piloter son robot d'allocation, comprendre ses dépôts et retraits SEPA IBAN via Stripe (minimum 10,00 €), " +
                        "et ajuster son profil de risque (Prudent, Équilibré, Dynamique). " +
                        "Sois concis, structuré, transparent sur les risques, sans jamais inventer de chiffres de transaction non présents dans le contexte."
                )
            }
        )
    }

    suspend fun generatePortfolioAnalysis(
        wallet: UserWallet,
        transactions: List<SepaTransaction>,
    ): Result<RobotStrategyPlan> {
        return try {
            val balanceText = IbanValidator.formatEuroFromCents(wallet.balanceCents)
            val pendingText = IbanValidator.formatEuroFromCents(wallet.pendingDepositCents)
            val recentSummary = if (transactions.isEmpty()) {
                "Aucune transaction SEPA enregistrée pour le moment."
            } else {
                transactions.take(5).joinToString("; ") { tx ->
                    "${tx.typeEnum.label} de ${IbanValidator.formatEuroFromCents(tx.amountCents)} (${tx.statusEnum.label})"
                }
            }

            val prompt = """
                Analyse le portefeuille KairoIA suivant et génère une stratégie adaptée :
                - Profil de risque sélectionné : ${wallet.riskProfileEnum.label} (${wallet.riskProfileEnum.subtitle})
                - Statut du Robot IA : ${if (wallet.botActive) "Actif" else "En pause"}
                - Solde disponible : $balanceText
                - Dépôts SEPA en cours : $pendingText
                - Historique récent : $recentSummary
                
                Génère une allocation réaliste en français adaptée au solde actuel (et si le solde est 0 €, propose la stratégie prête dès le premier dépôt SEPA de 10 € minimum).
            """.trimIndent()

            val response = withTimeout(AI_TIMEOUT_MS) {
                structuredModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Le modèle IA a renvoyé une réponse vide.")
            val json = JSONObject(jsonText)

            val allocationsJson = json.getJSONArray("allocations")
            val allocations = (0 until allocationsJson.length()).map { idx ->
                val item = allocationsJson.getJSONObject(idx)
                AssetAllocationItem(
                    assetName = item.getString("assetName"),
                    percentage = item.getInt("percentage"),
                    rationale = item.getString("rationale"),
                )
            }

            val stepsJson = json.getJSONArray("nextSteps")
            val steps = (0 until stepsJson.length()).map { idx ->
                stepsJson.getString(idx)
            }

            Result.success(
                RobotStrategyPlan(
                    summary = json.getString("summary"),
                    recommendedAction = json.getString("recommendedAction"),
                    riskScore = json.getInt("riskScore").coerceIn(1, 10),
                    projectedAnnualYieldPercent = json.getDouble("projectedAnnualYieldPercent"),
                    allocations = allocations,
                    nextSteps = steps,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate AI portfolio analysis", e)
            Result.failure(e)
        }
    }

    fun streamRobotReply(
        userPrompt: String,
        wallet: UserWallet,
        transactions: List<SepaTransaction>,
    ): Flow<String> = flow {
        val balanceText = IbanValidator.formatEuroFromCents(wallet.balanceCents)
        val pendingText = IbanValidator.formatEuroFromCents(wallet.pendingDepositCents)
        val promptWithContext = """
            [Contexte compte utilisateur]
            Solde disponible : $balanceText | En attente SEPA : $pendingText
            Robot IA : ${if (wallet.botActive) "Actif" else "En pause"} | Profil : ${wallet.riskProfileEnum.label}
            Nombre de transactions SEPA : ${transactions.size}
            
            Question de l'utilisateur :
            $userPrompt
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            chatModel.generateContentStream(promptWithContext).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("KairoBot n'a renvoyé aucune réponse.")
        }
    }
}
