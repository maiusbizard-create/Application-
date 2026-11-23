package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class RiskProfile(val label: String, val subtitle: String, val targetYieldLabel: String) {
    PRUDENT("Prudent", "Préservation du capital & liquidité SEPA", "3,5 % - 5,0 % / an"),
    EQUILIBRE("Équilibré", "Croissance modérée & diversification ETF", "6,0 % - 8,5 % / an"),
    DYNAMIQUE("Dynamique", "Performance IA active & momentum", "9,0 % - 14,0 % / an");

    companion object {
        fun fromRaw(raw: String?): RiskProfile =
            entries.firstOrNull { it.name == raw } ?: EQUILIBRE
    }
}

enum class TxType(val label: String) {
    DEPOSIT("Dépôt SEPA"),
    WITHDRAWAL("Retrait IBAN");

    companion object {
        fun fromRaw(raw: String?): TxType =
            entries.firstOrNull { it.name == raw } ?: DEPOSIT
    }
}

enum class TxStatus(val label: String) {
    PROCESSING("En cours SEPA"),
    SUCCEEDED("Confirmé"),
    FAILED("Échoué"),
    CANCELED("Annulé");

    companion object {
        fun fromRaw(raw: String?): TxStatus =
            entries.firstOrNull { it.name == raw } ?: PROCESSING
    }
}

enum class ChatSender {
    USER,
    BOT;

    companion object {
        fun fromRaw(raw: String?): ChatSender =
            entries.firstOrNull { it.name == raw } ?: BOT
    }
}

@Keep
@IgnoreExtraProperties
data class UserWallet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val stripeCustomerId: String? = null,
    val savedIbanMasked: String? = null,
    val balanceCents: Long = 0L,
    val pendingDepositCents: Long = 0L,
    val botActive: Boolean = true,
    val botRiskProfile: String = RiskProfile.EQUILIBRE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val riskProfileEnum: RiskProfile
        get() = RiskProfile.fromRaw(botRiskProfile)
}

@Keep
@IgnoreExtraProperties
data class SepaTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = TxType.DEPOSIT.name,
    val amountCents: Long = 0L,
    val currency: String = "EUR",
    val ibanMasked: String = "",
    val accountHolderName: String = "",
    val stripeId: String = "",
    val status: String = TxStatus.PROCESSING.name,
    val balanceCredited: Boolean = false,
    val failureReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val typeEnum: TxType get() = TxType.fromRaw(type)
    val statusEnum: TxStatus get() = TxStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AiAnalysisRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val riskProfile: String = RiskProfile.EQUILIBRE.name,
    val recommendedAction: String = "",
    val riskScore: Long = 5L,
    val projectedYieldPercent: Double = 0.0,
    val summary: String = "",
    val allocationsText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class BotChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = ChatSender.BOT.name,
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    val senderEnum: ChatSender get() = ChatSender.fromRaw(sender)
}

data class AssetAllocationItem(
    val assetName: String,
    val percentage: Int,
    val rationale: String,
)

data class RobotStrategyPlan(
    val summary: String,
    val recommendedAction: String,
    val riskScore: Int,
    val projectedAnnualYieldPercent: Double,
    val allocations: List<AssetAllocationItem>,
    val nextSteps: List<String>,
)
