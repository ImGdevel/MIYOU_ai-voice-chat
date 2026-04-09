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
			val sessionId = ConversationSessionFixture.createId()
			val transaction = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.DEDUCT,
				ConversationDeduction(sessionId),
				100L,
				5000L,
				4900L,
			)

			assertThat(transaction.transactionId()).isNotNull()
			assertThat(transaction.transactionId().value()).isNotBlank()
			assertThat(transaction.createdAt()).isNotNull()
		}

		@Test
		@DisplayName("차감 거래는 차감 전후 잔액을 정확히 기록한다")
		fun of_deduction_recordsCorrectBalances() {
			val sessionId = ConversationSessionFixture.createId()
			val transaction = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.DEDUCT,
				ConversationDeduction(sessionId),
				100L,
				5000L,
				4900L,
			)

			assertThat(transaction.type()).isEqualTo(CreditTransactionType.DEDUCT)
			assertThat(transaction.amount()).isEqualTo(100L)
			assertThat(transaction.balanceBefore()).isEqualTo(5000L)
			assertThat(transaction.balanceAfter()).isEqualTo(4900L)
			assertThat(transaction.source()).isInstanceOf(ConversationDeduction::class.java)
		}

		@Test
		@DisplayName("referenceId를 포함한 팩토리 메서드는 값을 정확히 저장한다")
		fun of_withReferenceId_setsField() {
			val transaction = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.CHARGE,
				SignupBonus(),
				5000L,
				0L,
				5000L,
				"ref-123",
			)

			assertThat(transaction.referenceId()).isEqualTo("ref-123")
		}

		@Test
		@DisplayName("amount가 0 이하이면 예외가 발생한다")
		fun of_zeroAmount_throws() {
			assertThatThrownBy {
				CreditTransaction.of(
					UserIdFixture.create(),
					CreditTransactionType.DEDUCT,
					SignupBonus(),
					0L,
					1000L,
					1000L,
				)
			}
				.isInstanceOf(IllegalArgumentException::class.java)
				.hasMessageContaining("amount must be positive")
		}

		@Test
		@DisplayName("팩토리를 여러 번 호출하면 transactionId가 매번 다르다")
		fun of_uniqueTransactionId_eachCall() {
			val transaction1 = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.DEDUCT,
				ConversationDeduction(ConversationSessionFixture.createId()),
				100L,
				5000L,
				4900L,
			)
			val transaction2 = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.DEDUCT,
				ConversationDeduction(ConversationSessionFixture.createId()),
				100L,
				5000L,
				4900L,
			)

			assertThat(transaction1.transactionId().value())
				.isNotEqualTo(transaction2.transactionId().value())
		}
	}

	@Nested
	@DisplayName("CreditSource 구현체 sourceType")
	inner class SourceType {

		@Test
		@DisplayName("ConversationDeduction의 sourceType은 CONVERSATION_DEDUCTION이다")
		fun conversationDeduction_sourceType() {
			val source = ConversationDeduction(ConversationSessionFixture.createId())

			assertThat(source.sourceType()).isEqualTo(CreditSourceType.CONVERSATION_DEDUCTION)
		}

		@Test
		@DisplayName("SignupBonus의 sourceType은 SIGNUP_BONUS다")
		fun signupBonus_sourceType() {
			assertThat(SignupBonus().sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS)
		}
	}
}
