package com.miyou.app.infrastructure.credit.document

import com.miyou.app.domain.credit.model.ConversationDeduction
import com.miyou.app.domain.credit.model.CreditSourceType
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.MissionReward
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.SignupBonus
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.fixture.UserIdFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("CreditTransactionDocument 직렬화 역직렬화")
class CreditTransactionDocumentTest {
    @Nested
    @DisplayName("ConversationDeduction 직렬화")
    inner class ConversationDeductionSerialization {
        @Test
        @DisplayName("fromDomain() 후 toDomain() 시 모든 필드가 일치한다")
        fun roundTrip_conversationDeduction() {
            val sessionId = ConversationSessionFixture.createId("session-abc")
            val original =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.DEDUCT,
                    ConversationDeduction(sessionId),
                    100L,
                    5000L,
                    4900L,
                    "session-abc",
                )

            val doc = CreditTransactionDocument.fromDomain(original)
            val restored = doc.toDomain()

            assertThat(restored.transactionId()).isEqualTo(original.transactionId())
            assertThat(restored.userId()).isEqualTo(original.userId())
            assertThat(restored.type()).isEqualTo(CreditTransactionType.DEDUCT)
            assertThat(restored.amount()).isEqualTo(100L)
            assertThat(restored.balanceBefore()).isEqualTo(5000L)
            assertThat(restored.balanceAfter()).isEqualTo(4900L)
            assertThat(restored.referenceId()).isEqualTo("session-abc")
            assertThat(restored.source().sourceType()).isEqualTo(CreditSourceType.CONVERSATION_DEDUCTION)

            val source = restored.source() as ConversationDeduction
            assertThat(source.sessionId().value()).isEqualTo("session-abc")
        }

        @Test
        @DisplayName("Document의 sourceType 필드는 CONVERSATION_DEDUCTION으로 저장된다")
        fun fromDomain_setsCorrectSourceType() {
            val transaction =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.DEDUCT,
                    ConversationDeduction(ConversationSessionFixture.createId()),
                    100L,
                    5000L,
                    4900L,
                )

            val doc = CreditTransactionDocument.fromDomain(transaction)

            assertThat(doc.sourceType()).isEqualTo("CONVERSATION_DEDUCTION")
            assertThat(doc.sourceData()).containsKey("sessionId")
        }
    }

    @Nested
    @DisplayName("SignupBonus 직렬화")
    inner class SignupBonusSerialization {
        @Test
        @DisplayName("SignupBonus는 sourceType이 SIGNUP_BONUS다")
        fun roundTrip_signupBonus() {
            val original =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.CHARGE,
                    SignupBonus(),
                    5000L,
                    0L,
                    5000L,
                )

            val doc = CreditTransactionDocument.fromDomain(original)
            val restored = doc.toDomain()

            assertThat(doc.sourceType()).isEqualTo("SIGNUP_BONUS")
            assertThat(doc.sourceData()).isEmpty()
            assertThat(restored.source().sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS)
            assertThat(restored.source()).isInstanceOf(SignupBonus::class.java)
        }
    }

    @Nested
    @DisplayName("PaymentCharge 직렬화")
    inner class PaymentChargeSerialization {
        @Test
        @DisplayName("PaymentCharge는 paymentId와 pgProvider가 유지된다")
        fun roundTrip_paymentCharge() {
            val original =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.CHARGE,
                    PaymentCharge("pay-xyz-123", "toss"),
                    10000L,
                    0L,
                    10000L,
                )

            val doc = CreditTransactionDocument.fromDomain(original)
            val restored = doc.toDomain()

            assertThat(doc.sourceType()).isEqualTo("PAYMENT_CHARGE")
            assertThat(doc.sourceData()).containsEntry("paymentId", "pay-xyz-123")
            assertThat(doc.sourceData()).containsEntry("pgProvider", "toss")

            val source = restored.source() as PaymentCharge
            assertThat(source.paymentId()).isEqualTo("pay-xyz-123")
            assertThat(source.pgProvider()).isEqualTo("toss")
        }
    }

    @Nested
    @DisplayName("MissionReward 직렬화")
    inner class MissionRewardSerialization {
        @Test
        @DisplayName("MissionReward는 missionId와 missionType이 유지된다")
        fun roundTrip_missionReward() {
            val original =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.CHARGE,
                    MissionReward(MissionId.of("mission-share"), "SHARE_SERVICE"),
                    500L,
                    2000L,
                    2500L,
                )

            val doc = CreditTransactionDocument.fromDomain(original)
            val restored = doc.toDomain()

            assertThat(doc.sourceType()).isEqualTo("MISSION_REWARD")
            assertThat(doc.sourceData()).containsEntry("missionId", "mission-share")
            assertThat(doc.sourceData()).containsEntry("missionType", "SHARE_SERVICE")

            val source = restored.source() as MissionReward
            assertThat(source.missionId().value()).isEqualTo("mission-share")
            assertThat(source.missionType()).isEqualTo("SHARE_SERVICE")
        }
    }

    @Nested
    @DisplayName("알 수 없는 sourceType")
    inner class UnknownSourceType {
        @Test
        @DisplayName("알 수 없는 sourceType이면 역직렬화 시 IllegalArgumentException이 발생한다")
        fun toDomain_unknownSourceType_throws() {
            val document =
                CreditTransactionDocument(
                    "id",
                    "user-1",
                    "DEDUCT",
                    "UNKNOWN_TYPE",
                    mapOf(),
                    100L,
                    5000L,
                    4900L,
                    null,
                    Instant.now(),
                )

            assertThatThrownBy(document::toDomain)
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("UNKNOWN_TYPE")
        }
    }
}
