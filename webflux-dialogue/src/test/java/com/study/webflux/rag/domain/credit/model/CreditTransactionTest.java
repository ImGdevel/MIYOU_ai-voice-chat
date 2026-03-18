package com.study.webflux.rag.domain.credit.model;

import com.study.webflux.rag.domain.credit.model.ConversationDeduction;
import com.study.webflux.rag.domain.credit.model.SignupBonus;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.fixture.ConversationSessionFixture;
import com.study.webflux.rag.fixture.UserIdFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CreditTransaction 도메인 모델")
class CreditTransactionTest {

	@Nested
	@DisplayName("of() 팩토리")
	class Factory {

		@Test
		@DisplayName("팩토리로 생성 시 UUID transactionId와 현재 타임스탬프가 자동 설정된다")
		void of_setsTransactionIdAndCreatedAt() {
			ConversationSessionId sessionId = ConversationSessionFixture.createId();
			CreditTransaction tx = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.DEDUCT,
				new ConversationDeduction(sessionId),
				100L,
				5000L,
				4900L);

			assertThat(tx.transactionId()).isNotNull();
			assertThat(tx.transactionId().value()).isNotBlank();
			assertThat(tx.createdAt()).isNotNull();
		}

		@Test
		@DisplayName("차감 트랜잭션의 balanceBefore/After가 올바르게 기록된다")
		void of_deduction_recordsCorrectBalances() {
			ConversationSessionId sessionId = ConversationSessionFixture.createId();
			CreditTransaction tx = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.DEDUCT,
				new ConversationDeduction(sessionId),
				100L,
				5000L,
				4900L);

			assertThat(tx.type()).isEqualTo(CreditTransactionType.DEDUCT);
			assertThat(tx.amount()).isEqualTo(100L);
			assertThat(tx.balanceBefore()).isEqualTo(5000L);
			assertThat(tx.balanceAfter()).isEqualTo(4900L);
			assertThat(tx.source()).isInstanceOf(ConversationDeduction.class);
		}

		@Test
		@DisplayName("referenceId가 포함된 팩토리 메서드로 올바르게 생성된다")
		void of_withReferenceId_setsField() {
			CreditTransaction tx = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.CHARGE,
				new SignupBonus(),
				5000L,
				0L,
				5000L,
				"ref-123");

			assertThat(tx.referenceId()).isEqualTo("ref-123");
		}

		@Test
		@DisplayName("amount가 0 이하이면 예외 발생")
		void of_zeroAmount_throws() {
			assertThatThrownBy(() -> CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.DEDUCT,
				new SignupBonus(),
				0L,
				1000L,
				1000L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("amount must be positive");
		}

		@Test
		@DisplayName("매 호출마다 고유한 transactionId가 생성된다")
		void of_uniqueTransactionId_eachCall() {
			CreditTransaction tx1 = CreditTransaction.of(
				UserIdFixture.create(), CreditTransactionType.DEDUCT,
				new ConversationDeduction(ConversationSessionFixture.createId()),
				100L, 5000L, 4900L);
			CreditTransaction tx2 = CreditTransaction.of(
				UserIdFixture.create(), CreditTransactionType.DEDUCT,
				new ConversationDeduction(ConversationSessionFixture.createId()),
				100L, 5000L, 4900L);

			assertThat(tx1.transactionId().value()).isNotEqualTo(tx2.transactionId().value());
		}
	}

	@Nested
	@DisplayName("CreditSource 타입별 sourceType 반환")
	class SourceType {

		@Test
		@DisplayName("ConversationDeduction의 sourceType은 CONVERSATION_DEDUCTION")
		void conversationDeduction_sourceType() {
			ConversationDeduction source = new ConversationDeduction(
				ConversationSessionFixture.createId());
			assertThat(source.sourceType()).isEqualTo(CreditSourceType.CONVERSATION_DEDUCTION);
		}

		@Test
		@DisplayName("SignupBonus의 sourceType은 SIGNUP_BONUS")
		void signupBonus_sourceType() {
			assertThat(new SignupBonus().sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS);
		}
	}
}
