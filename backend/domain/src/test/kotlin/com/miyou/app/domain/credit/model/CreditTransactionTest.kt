package com.miyou.app.domain.credit.model

import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.fixture.UserIdFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CreditTransaction 도메인 모델")
class CreditTransactionTest {
    @Nested
    @DisplayName("of() 팩토리")
    inner class Factory {
        @Test
        @DisplayName("팩토리로 생성하면 transactionId와 생성 시간이 자동 설정된다")
        fun of_setsTransactionIdAndCreatedAt() {
            // given: 대화 세션 ID 및 테스트 데이터 준비
            val sessionId = ConversationSessionFixture.createId()

            // when: 팩토리 메서드를 통해 거래 내역 생성
            val transaction =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.DEDUCT,
                    ConversationDeduction(sessionId.value),
                    100L,
                    5000L,
                    4900L,
                )

            // then: transactionId 및 생성 시간이 누락 없이 자동 설정되었는지 확인
            assertThat(transaction.transactionId).isNotNull()
            assertThat(transaction.transactionId.value).isNotBlank()
            assertThat(transaction.createdAt).isNotNull()
        }

        @Test
        @DisplayName("차감 거래는 차감 전후 잔액을 정확히 기록한다")
        fun of_deduction_recordsCorrectBalances() {
            // given: 대화 세션 ID 준비
            val sessionId = ConversationSessionFixture.createId()

            // when: 차감 거래 내역 생성
            val transaction =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.DEDUCT,
                    ConversationDeduction(sessionId.value),
                    100L,
                    5000L,
                    4900L,
                )

            // then: 차감 타입, 금액, 차감 전/후 잔액 및 거래 출처 타입 검증
            assertThat(transaction.type).isEqualTo(CreditTransactionType.DEDUCT)
            assertThat(transaction.amount).isEqualTo(100L)
            assertThat(transaction.balanceBefore).isEqualTo(5000L)
            assertThat(transaction.balanceAfter).isEqualTo(4900L)
            assertThat(transaction.source).isInstanceOf(ConversationDeduction::class.java)
        }

        @Test
        @DisplayName("referenceId를 포함한 팩토리 메서드는 값을 정확히 저장한다")
        fun of_withReferenceId_setsField() {
            // when: 충전 거래 내역을 참조 ID와 함께 생성
            val transaction =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.CHARGE,
                    SignupBonus,
                    5000L,
                    0L,
                    5000L,
                    "ref-123",
                )

            // then: 저장된 참조 ID 확인
            assertThat(transaction.referenceId).isEqualTo("ref-123")
        }

        @Test
        @DisplayName("amount가 0 이하이면 예외가 발생한다")
        fun of_zeroAmount_throws() {
            // when & then: 금액이 0일 때 IllegalArgumentException 예외 발생 검증
            assertThatThrownBy {
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.DEDUCT,
                    SignupBonus,
                    0L,
                    1000L,
                    1000L,
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("amount must be positive")
        }

        @Test
        @DisplayName("팩토리를 여러 번 호출하면 transactionId가 매번 다르다")
        fun of_uniqueTransactionId_eachCall() {
            // when: 각각 별도로 두 번의 거래 생성 호출
            val transaction1 =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.DEDUCT,
                    ConversationDeduction(ConversationSessionFixture.createId().value),
                    100L,
                    5000L,
                    4900L,
                )
            val transaction2 =
                CreditTransaction.of(
                    UserIdFixture.create(),
                    CreditTransactionType.DEDUCT,
                    ConversationDeduction(ConversationSessionFixture.createId().value),
                    100L,
                    5000L,
                    4900L,
                )

            // then: 두 거래 내역의 transactionId 고유값 여부 검증
            assertThat(transaction1.transactionId.value)
                .isNotEqualTo(transaction2.transactionId.value)
        }
    }

    @Nested
    @DisplayName("CreditSource 구현체 sourceType")
    inner class SourceType {
        @Test
        @DisplayName("ConversationDeduction의 sourceType은 CONVERSATION_DEDUCTION이다")
        fun conversationDeduction_sourceType() {
            // given: 대화 세션 ID 기반의 차감 소스 생성
            val source = ConversationDeduction(ConversationSessionFixture.createId().value)

            // when & then: 소스 타입 검증
            assertThat(source.sourceType()).isEqualTo(CreditSourceType.CONVERSATION_DEDUCTION)
        }

        @Test
        @DisplayName("SignupBonus의 sourceType은 SIGNUP_BONUS다")
        fun signupBonus_sourceType() {
            // when & then: 가입 보너스 소스 타입 검증
            assertThat(SignupBonus.sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS)
        }
    }
}
