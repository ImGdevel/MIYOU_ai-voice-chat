package com.study.webflux.rag.infrastructure.credit.document;

import com.study.webflux.rag.domain.credit.model.CreditTransaction;
import com.study.webflux.rag.domain.credit.model.CreditTransactionType;
import com.study.webflux.rag.domain.credit.model.CreditSourceType;
import com.study.webflux.rag.domain.credit.model.ConversationDeduction;
import com.study.webflux.rag.domain.credit.model.MissionReward;
import com.study.webflux.rag.domain.credit.model.PaymentCharge;
import com.study.webflux.rag.domain.credit.model.SignupBonus;
import com.study.webflux.rag.domain.mission.model.MissionId;
import com.study.webflux.rag.fixture.ConversationSessionFixture;
import com.study.webflux.rag.fixture.UserIdFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CreditTransactionDocument 직렬화/역직렬화")
class CreditTransactionDocumentTest {

	@Nested
	@DisplayName("ConversationDeduction 직렬화")
	class ConversationDeductionSerialization {

		@Test
		@DisplayName("fromDomain() → toDomain() 왕복 변환 시 모든 필드가 일치한다")
		void roundTrip_conversationDeduction() {
			var sessionId = ConversationSessionFixture.createId("session-abc");
			CreditTransaction original = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.DEDUCT,
				new ConversationDeduction(sessionId),
				100L, 5000L, 4900L, "session-abc");

			CreditTransactionDocument doc = CreditTransactionDocument.fromDomain(original);
			CreditTransaction restored = doc.toDomain();

			assertThat(restored.transactionId()).isEqualTo(original.transactionId());
			assertThat(restored.userId()).isEqualTo(original.userId());
			assertThat(restored.type()).isEqualTo(CreditTransactionType.DEDUCT);
			assertThat(restored.amount()).isEqualTo(100L);
			assertThat(restored.balanceBefore()).isEqualTo(5000L);
			assertThat(restored.balanceAfter()).isEqualTo(4900L);
			assertThat(restored.referenceId()).isEqualTo("session-abc");
			assertThat(restored.source().sourceType()).isEqualTo(CreditSourceType.CONVERSATION_DEDUCTION);

			ConversationDeduction source = (ConversationDeduction) restored.source();
			assertThat(source.sessionId().value()).isEqualTo("session-abc");
		}

		@Test
		@DisplayName("Document의 sourceType 필드에 'CONVERSATION_DEDUCTION'이 저장된다")
		void fromDomain_setsCorrectSourceType() {
			CreditTransaction tx = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.DEDUCT,
				new ConversationDeduction(ConversationSessionFixture.createId()),
				100L, 5000L, 4900L);

			CreditTransactionDocument doc = CreditTransactionDocument.fromDomain(tx);

			assertThat(doc.sourceType()).isEqualTo("CONVERSATION_DEDUCTION");
			assertThat(doc.sourceData()).containsKey("sessionId");
		}
	}

	@Nested
	@DisplayName("SignupBonus 직렬화")
	class SignupBonusSerialization {

		@Test
		@DisplayName("SignupBonus 왕복 변환 시 sourceType이 SIGNUP_BONUS")
		void roundTrip_signupBonus() {
			CreditTransaction original = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.CHARGE,
				new SignupBonus(),
				5000L, 0L, 5000L);

			CreditTransactionDocument doc = CreditTransactionDocument.fromDomain(original);
			CreditTransaction restored = doc.toDomain();

			assertThat(doc.sourceType()).isEqualTo("SIGNUP_BONUS");
			assertThat(doc.sourceData()).isEmpty();
			assertThat(restored.source().sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS);
			assertThat(restored.source()).isInstanceOf(SignupBonus.class);
		}
	}

	@Nested
	@DisplayName("PaymentCharge 직렬화")
	class PaymentChargeSerialization {

		@Test
		@DisplayName("PaymentCharge 왕복 변환 시 paymentId와 pgProvider가 유지된다")
		void roundTrip_paymentCharge() {
			CreditTransaction original = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.CHARGE,
				new PaymentCharge("pay-xyz-123", "toss"),
				10000L, 0L, 10000L);

			CreditTransactionDocument doc = CreditTransactionDocument.fromDomain(original);
			CreditTransaction restored = doc.toDomain();

			assertThat(doc.sourceType()).isEqualTo("PAYMENT_CHARGE");
			assertThat(doc.sourceData()).containsEntry("paymentId", "pay-xyz-123");
			assertThat(doc.sourceData()).containsEntry("pgProvider", "toss");

			PaymentCharge source = (PaymentCharge) restored.source();
			assertThat(source.paymentId()).isEqualTo("pay-xyz-123");
			assertThat(source.pgProvider()).isEqualTo("toss");
		}
	}

	@Nested
	@DisplayName("MissionReward 직렬화")
	class MissionRewardSerialization {

		@Test
		@DisplayName("MissionReward 왕복 변환 시 missionId와 missionType이 유지된다")
		void roundTrip_missionReward() {
			CreditTransaction original = CreditTransaction.of(
				UserIdFixture.create(),
				CreditTransactionType.CHARGE,
				new MissionReward(MissionId.of("mission-share"), "SHARE_SERVICE"),
				500L, 2000L, 2500L);

			CreditTransactionDocument doc = CreditTransactionDocument.fromDomain(original);
			CreditTransaction restored = doc.toDomain();

			assertThat(doc.sourceType()).isEqualTo("MISSION_REWARD");
			assertThat(doc.sourceData()).containsEntry("missionId", "mission-share");
			assertThat(doc.sourceData()).containsEntry("missionType", "SHARE_SERVICE");

			MissionReward source = (MissionReward) restored.source();
			assertThat(source.missionId().value()).isEqualTo("mission-share");
			assertThat(source.missionType()).isEqualTo("SHARE_SERVICE");
		}
	}

	@Nested
	@DisplayName("알 수 없는 sourceType")
	class UnknownSourceType {

		@Test
		@DisplayName("알 수 없는 sourceType으로 역직렬화 시 IllegalArgumentException 발생")
		void toDomain_unknownSourceType_throws() {
			CreditTransactionDocument doc = new CreditTransactionDocument(
				"id", "user-1", "DEDUCT", "UNKNOWN_TYPE",
				java.util.Map.of(), 100L, 5000L, 4900L, null, java.time.Instant.now());

			assertThatThrownBy(doc::toDomain)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("UNKNOWN_TYPE");
		}
	}
}
