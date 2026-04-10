package com.miyou.app.infrastructure.credit.document

import com.miyou.app.domain.credit.model.ConversationDeduction
import com.miyou.app.domain.credit.model.CreditSource
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionId
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.MissionReward
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.SignupBonus
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.mission.model.MissionId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.HashMap

@Document(collection = "credit_transactions")
@CompoundIndexes(
    CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}"),
)
data class CreditTransactionDocument(
    @Id val id: String,
    @Indexed val userId: String,
    val type: String,
    val sourceType: String,
    val sourceData: Map<String, String>,
    val amount: Long,
    val balanceBefore: Long,
    val balanceAfter: Long,
    val referenceId: String?,
    val createdAt: Instant?,
) {
    companion object {
        fun fromDomain(tx: CreditTransaction): CreditTransactionDocument =
            CreditTransactionDocument(
                tx.transactionId().value(),
                tx.userId().value(),
                tx.type().name,
                tx.source().sourceType().name,
                serializeSource(tx.source()),
                tx.amount(),
                tx.balanceBefore(),
                tx.balanceAfter(),
                tx.referenceId(),
                tx.createdAt(),
            )

        private fun serializeSource(source: CreditSource): Map<String, String> {
            val data: MutableMap<String, String> = HashMap()
            when (source) {
                is ConversationDeduction -> {
                    data["sessionId"] = source.sessionId().value()
                }

                is PaymentCharge -> {
                    data["paymentId"] = source.paymentId()
                    data["pgProvider"] = source.pgProvider()
                }

                is MissionReward -> {
                    data["missionId"] = source.missionId().value()
                    data["missionType"] = source.missionType()
                }

                is SignupBonus -> {
                    Unit
                }
            }
            return data
        }

        private fun deserializeSource(
            sourceType: String,
            sourceData: Map<String, String>,
        ): CreditSource =
            when (sourceType) {
                "CONVERSATION_DEDUCTION" -> {
                    ConversationDeduction(
                        ConversationSessionId.of(sourceData["sessionId"] ?: ""),
                    )
                }

                "SIGNUP_BONUS" -> {
                    SignupBonus()
                }

                "PAYMENT_CHARGE" -> {
                    PaymentCharge(
                        sourceData["paymentId"] ?: "",
                        sourceData["pgProvider"] ?: "",
                    )
                }

                "MISSION_REWARD" -> {
                    MissionReward(
                        MissionId.of(sourceData["missionId"] ?: ""),
                        sourceData["missionType"] ?: "",
                    )
                }

                else -> {
                    throw IllegalArgumentException("Unsupported CreditSourceType: $sourceType")
                }
            }
    }

    fun toDomain(): CreditTransaction =
        CreditTransaction(
            CreditTransactionId.of(id),
            UserId.of(userId),
            CreditTransactionType.valueOf(type),
            deserializeSource(sourceType, sourceData),
            amount,
            balanceBefore,
            balanceAfter,
            referenceId,
            createdAt,
        )
}
