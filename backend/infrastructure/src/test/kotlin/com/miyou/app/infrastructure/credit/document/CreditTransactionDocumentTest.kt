package com.miyou.app.infrastructure.credit.document

import com.miyou.app.domain.credit.model.ConversationDeduction
import com.miyou.app.domain.credit.model.CreditSourceType
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.MissionReward
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.SignupBonus
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.fixture.UserIdFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("CreditTransactionDocument")
class CreditTransactionDocumentTest {
    @Nested
    @DisplayName("ConversationDeduction 직렬화 테스트")
    inner class ConversationDeductionSerialization {
        @Test
        @DisplayName("대화 차감 거래를 문서로 변환한 뒤 복원한다")
        fun roundTrip_conversationDeduction() {
            // given: 원래 거래 내역 준비
            val sessionId = ConversationSessionFixture.createId("session-abc")
            val original =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.DEDUCT,
                    ConversationDeduction(sessionId.value),
                    100L,
                    5000L,
                    4900L,
                    "session-abc",
                )

            // when: 도메인 객체 -> DB 다큐먼트 변환 -> 도메인 객체 복원
            val doc = CreditTransactionDocument.fromDomain(original)
            val restored = doc.toDomain()

            // then: 복원된 객체의 세부 정보 일치 여부 확인
            assertThat(restored.transactionId).isEqualTo(original.transactionId)
            assertThat(restored.userId).isEqualTo(original.userId)
            assertThat(restored.type).isEqualTo(CreditTransactionType.DEDUCT)
            assertThat(restored.amount).isEqualTo(100L)
            assertThat(restored.balanceBefore).isEqualTo(5000L)
            assertThat(restored.balanceAfter).isEqualTo(4900L)
            assertThat(restored.referenceId).isEqualTo("session-abc")
            assertThat(restored.source.sourceType()).isEqualTo(CreditSourceType.CONVERSATION_DEDUCTION)

            val source = restored.source as ConversationDeduction
            assertThat(source.sessionId()).isEqualTo("session-abc")
        }

        @Test
        @DisplayName("대화 차감 출처 타입과 데이터를 올바르게 저장한다")
        fun fromDomain_setsCorrectSourceType() {
            // given: 대화 차감 거래 생성
            val transaction =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.DEDUCT,
                    ConversationDeduction(ConversationSessionFixture.createId().value),
                    100L,
                    5000L,
                    4900L,
                )

            // when: 도메인 객체 -> 다큐먼트 변환
            val doc = CreditTransactionDocument.fromDomain(transaction)

            // then: 다큐먼트에 소스 타입과 세션 ID 데이터 맵이 잘 생성되었는지 검증
            assertThat(doc.sourceType).isEqualTo("CONVERSATION_DEDUCTION")
            assertThat(doc.sourceData).containsKey("sessionId")
        }
    }

    @Nested
    @DisplayName("SignupBonus 직렬화 테스트")
    inner class SignupBonusSerialization {
        @Test
        @DisplayName("가입 보너스는 빈 payload로 저장한다")
        fun roundTrip_signupBonus() {
            // given: 가입 보너스 충전 내역 준비
            val original =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.CHARGE,
                    SignupBonus,
                    5000L,
                    0L,
                    5000L,
                )

            // when: 변환 및 복원
            val doc = CreditTransactionDocument.fromDomain(original)
            val restored = doc.toDomain()

            // then: 소스 타입 검증 및 복원 소스 객체 타입 검증
            assertThat(doc.sourceType).isEqualTo("SIGNUP_BONUS")
            assertThat(doc.sourceData).isEmpty()
            assertThat(restored.source.sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS)
            assertThat(restored.source).isInstanceOf(SignupBonus::class.java)
        }
    }

    @Nested
    @DisplayName("PaymentCharge 직렬화 테스트")
    inner class PaymentChargeSerialization {
        @Test
        @DisplayName("결제 ID와 제공자를 저장한다")
        fun roundTrip_paymentCharge() {
            // given: 결제 충전 거래 내역 준비
            val original =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.CHARGE,
                    PaymentCharge("pay-xyz-123", "toss"),
                    10000L,
                    0L,
                    10000L,
                )

            // when: 다큐먼트 변환 및 도메인 객체 복원
            val doc = CreditTransactionDocument.fromDomain(original)
            val restored = doc.toDomain()

            // then: 결제 상세 메타데이터 직렬화 검증 및 도메인 복원 검증
            assertThat(doc.sourceType).isEqualTo("PAYMENT_CHARGE")
            assertThat(doc.sourceData).containsEntry("paymentId", "pay-xyz-123")
            assertThat(doc.sourceData).containsEntry("pgProvider", "toss")

            val source = restored.source as PaymentCharge
            assertThat(source.paymentId()).isEqualTo("pay-xyz-123")
            assertThat(source.pgProvider()).isEqualTo("toss")
        }
    }

    @Nested
    @DisplayName("MissionReward 직렬화 테스트")
    inner class MissionRewardSerialization {
        @Test
        @DisplayName("미션 ID와 타입을 저장한다")
        fun roundTrip_missionReward() {
            // given: 미션 리워드 거래 내역 준비
            val original =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.CHARGE,
                    MissionReward("mission-share", "SHARE_SERVICE"),
                    500L,
                    2000L,
                    2500L,
                )

            // when: 변환 및 복원
            val doc = CreditTransactionDocument.fromDomain(original)
            val restored = doc.toDomain()

            // then: 미션 보상 상세 필드 직렬화 검증 및 도메인 복원 검증
            assertThat(doc.sourceType).isEqualTo("MISSION_REWARD")
            assertThat(doc.sourceData).containsEntry("missionId", "mission-share")
            assertThat(doc.sourceData).containsEntry("missionType", "SHARE_SERVICE")

            val source = restored.source as MissionReward
            assertThat(source.missionId()).isEqualTo("mission-share")
            assertThat(source.missionType()).isEqualTo("SHARE_SERVICE")
        }
    }

    @Nested
    @DisplayName("알 수 없는 출처 타입 테스트")
    inner class UnknownSourceType {
        @Test
        @DisplayName("지원하지 않는 출처 타입이면 예외가 발생한다")
        fun toDomain_unknownSourceType_throws() {
            // given: 알 수 없는 타입명인 다큐먼트 직접 생성
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

            // when & then: 도메인 복원 시 IllegalArgumentException 발생 검증
            assertThatThrownBy(document::toDomain)
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("UNKNOWN_TYPE")
        }
    }
}
