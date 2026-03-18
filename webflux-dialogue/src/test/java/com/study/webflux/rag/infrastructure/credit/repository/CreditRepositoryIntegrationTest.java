package com.study.webflux.rag.infrastructure.credit.repository;

import com.study.webflux.rag.config.annotation.ReactiveRepositoryTest;
import com.study.webflux.rag.domain.credit.model.CreditSourceType;
import com.study.webflux.rag.domain.credit.model.CreditTransaction;
import com.study.webflux.rag.domain.credit.model.CreditTransactionType;
import com.study.webflux.rag.domain.credit.model.UserCredit;
import com.study.webflux.rag.domain.credit.model.ConversationDeduction;
import com.study.webflux.rag.domain.credit.model.PaymentCharge;
import com.study.webflux.rag.domain.credit.model.SignupBonus;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.fixture.ConversationSessionFixture;
import com.study.webflux.rag.infrastructure.credit.adapter.CreditTransactionMongoAdapter;
import com.study.webflux.rag.infrastructure.credit.adapter.UserCreditMongoAdapter;
import com.study.webflux.rag.infrastructure.credit.document.CreditTransactionDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MongoDB 실제 연결을 통한 Credit 리포지토리 통합 테스트.
 *
 * 전제: 로컬 MongoDB 인스턴스 (application-test.yml 참조)
 * - URI: mongodb://localhost:27017/ragdb-test
 *
 * 검증 항목:
 * - UserCredit CRUD 및 userId unique 제약
 * - CreditTransaction 저장 및 userId + createdAt 복합 인덱스 정렬
 * - 모든 CreditSource 타입의 직렬화/역직렬화 정합성
 */
@ReactiveRepositoryTest
@Import({UserCreditMongoAdapter.class, CreditTransactionMongoAdapter.class})
@DisplayName("[통합] Credit MongoDB Repository")
class CreditRepositoryIntegrationTest {

	@Autowired
	private UserCreditMongoRepository userCreditRepo;

	@Autowired
	private CreditTransactionMongoRepository creditTxRepo;

	@Autowired
	private UserCreditMongoAdapter userCreditAdapter;

	@Autowired
	private CreditTransactionMongoAdapter creditTxAdapter;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		userCreditRepo.deleteAll().block();
		creditTxRepo.deleteAll().block();
	}

	// ── UserCredit CRUD ────────────────────────────────────────────────────

	@Nested
	@DisplayName("UserCredit 저장 및 조회")
	class UserCreditCrud {

		@Test
		@DisplayName("신규 UserCredit 저장 후 userId로 조회하면 저장된 값과 일치한다")
		void save_thenFindByUserId_returnsCorrectBalance() {
			UserId userId = UserId.of("integration-user-1");
			UserCredit credit = UserCredit.initialize(userId, 5000L);

			StepVerifier.create(
				userCreditAdapter.save(credit)
					.then(userCreditAdapter.findByUserId(userId)))
				.assertNext(found -> {
					assertThat(found.userId()).isEqualTo(userId);
					assertThat(found.balance()).isEqualTo(5000L);
				})
				.verifyComplete();
		}

		@Test
		@DisplayName("잔액 차감 후 재저장하면 갱신된 잔액이 조회된다")
		void deduct_thenSave_updatesBalance() {
			UserId userId = UserId.of("integration-user-deduct");
			UserCredit initial = UserCredit.initialize(userId, 5000L);

			StepVerifier.create(
				userCreditAdapter.save(initial)
					.map(saved -> saved.deduct(100L))
					.flatMap(userCreditAdapter::save)
					.then(userCreditAdapter.findByUserId(userId)))
				.assertNext(found -> assertThat(found.balance()).isEqualTo(4900L))
				.verifyComplete();
		}

		@Test
		@DisplayName("존재하지 않는 userId 조회 시 빈 Mono를 반환한다")
		void findByUserId_notExisting_returnsEmpty() {
			UserId userId = UserId.of("nonexistent-user");

			StepVerifier.create(userCreditAdapter.findByUserId(userId))
				.verifyComplete();
		}

		@Test
		@DisplayName("동일 userId로 두 번 저장하면 마지막 값으로 갱신된다 (upsert)")
		void save_sameUserTwice_lastValueWins() {
			UserId userId = UserId.of("integration-user-upsert");
			UserCredit first = UserCredit.initialize(userId, 1000L);
			UserCredit second = first.charge(4000L);

			StepVerifier.create(
				userCreditAdapter.save(first)
					.then(userCreditAdapter.save(second))
					.then(userCreditAdapter.findByUserId(userId)))
				.assertNext(found -> assertThat(found.balance()).isEqualTo(5000L))
				.verifyComplete();
		}
	}

	// ── CreditTransaction 저장 및 조회 ────────────────────────────────────

	@Nested
	@DisplayName("CreditTransaction 저장 및 페이지 조회")
	class CreditTransactionCrud {

		@Test
		@DisplayName("트랜잭션 저장 후 userId로 조회 시 최신순으로 반환된다")
		void saveMultiple_thenFindByUserId_returnsDescOrder() throws InterruptedException {
			UserId userId = UserId.of("tx-order-user");

			// 시간 순서가 다르도록 약간의 간격 삽입
			CreditTransaction tx1 = CreditTransaction.of(userId, CreditTransactionType.CHARGE,
				new SignupBonus(), 5000L, 0L, 5000L);
			Thread.sleep(5);
			CreditTransaction tx2 = CreditTransaction.of(userId, CreditTransactionType.DEDUCT,
				new ConversationDeduction(ConversationSessionFixture.createId()), 100L, 5000L, 4900L);
			Thread.sleep(5);
			CreditTransaction tx3 = CreditTransaction.of(userId, CreditTransactionType.DEDUCT,
				new ConversationDeduction(ConversationSessionFixture.createId()), 100L, 4900L, 4800L);

			StepVerifier.create(
				creditTxAdapter.save(tx1)
					.then(creditTxAdapter.save(tx2))
					.then(creditTxAdapter.save(tx3))
					.thenMany(creditTxAdapter.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10))))
				.assertNext(tx -> assertThat(tx.balanceBefore()).isEqualTo(4900L)) // tx3 (최신)
				.assertNext(tx -> assertThat(tx.balanceBefore()).isEqualTo(5000L)) // tx2
				.assertNext(tx -> assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE)) // tx1
				.verifyComplete();
		}

		@Test
		@DisplayName("페이지 사이즈 제한이 적용된다")
		void findByUserId_withPageSize_limitsResults() throws InterruptedException {
			UserId userId = UserId.of("tx-page-user");

			for (int i = 0; i < 5; i++) {
				CreditTransaction tx = CreditTransaction.of(userId, CreditTransactionType.DEDUCT,
					new ConversationDeduction(ConversationSessionFixture.createId()), 100L,
					5000L - (i * 100L), 4900L - (i * 100L));
				creditTxAdapter.save(tx).block();
				Thread.sleep(2);
			}

			StepVerifier.create(
				creditTxAdapter.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 3)))
				.expectNextCount(3)
				.verifyComplete();
		}

		@Test
		@DisplayName("다른 userId의 트랜잭션은 조회되지 않는다")
		void findByUserId_isolatesPerUser() {
			UserId userA = UserId.of("isolation-user-a");
			UserId userB = UserId.of("isolation-user-b");

			CreditTransaction txA = CreditTransaction.of(userA, CreditTransactionType.CHARGE,
				new SignupBonus(), 5000L, 0L, 5000L);
			CreditTransaction txB = CreditTransaction.of(userB, CreditTransactionType.CHARGE,
				new SignupBonus(), 5000L, 0L, 5000L);

			StepVerifier.create(
				creditTxAdapter.save(txA)
					.then(creditTxAdapter.save(txB))
					.thenMany(creditTxAdapter.findByUserIdOrderByCreatedAtDesc(userA, PageRequest.of(0, 10))))
				.assertNext(tx -> assertThat(tx.userId()).isEqualTo(userA))
				.verifyComplete();
		}
	}

	// ── CreditSource 타입별 직렬화 정합성 ─────────────────────────────────

	@Nested
	@DisplayName("CreditSource MongoDB 직렬화/역직렬화 정합성")
	class SourceTypePersistence {

		@Test
		@DisplayName("ConversationDeduction: sessionId가 MongoDB 저장 후에도 유지된다")
		void conversationDeduction_persistsSessionId() {
			UserId userId = UserId.of("source-deduction-user");
			var sessionId = ConversationSessionFixture.createId("my-session-xyz");
			CreditTransaction tx = CreditTransaction.of(userId, CreditTransactionType.DEDUCT,
				new ConversationDeduction(sessionId), 100L, 5000L, 4900L, "my-session-xyz");

			StepVerifier.create(
				creditTxAdapter.save(tx)
					.then(creditTxRepo.findById(tx.transactionId().value()))
					.map(CreditTransactionDocument::toDomain))
				.assertNext(restored -> {
					assertThat(restored.source().sourceType())
						.isEqualTo(CreditSourceType.CONVERSATION_DEDUCTION);
					ConversationDeduction src = (ConversationDeduction) restored.source();
					assertThat(src.sessionId().value()).isEqualTo("my-session-xyz");
					assertThat(restored.referenceId()).isEqualTo("my-session-xyz");
				})
				.verifyComplete();
		}

		@Test
		@DisplayName("SignupBonus: sourceData가 비어 있어도 역직렬화된다")
		void signupBonus_persistsWithEmptySourceData() {
			UserId userId = UserId.of("source-signup-user");
			CreditTransaction tx = CreditTransaction.of(userId, CreditTransactionType.CHARGE,
				new SignupBonus(), 5000L, 0L, 5000L);

			StepVerifier.create(
				creditTxAdapter.save(tx)
					.then(creditTxRepo.findById(tx.transactionId().value()))
					.map(CreditTransactionDocument::toDomain))
				.assertNext(restored -> {
					assertThat(restored.source().sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS);
					assertThat(restored.source()).isInstanceOf(SignupBonus.class);
				})
				.verifyComplete();
		}

		@Test
		@DisplayName("PaymentCharge: paymentId와 pgProvider가 MongoDB 저장 후에도 유지된다")
		void paymentCharge_persistsPaymentInfo() {
			UserId userId = UserId.of("source-payment-user");
			CreditTransaction tx = CreditTransaction.of(userId, CreditTransactionType.CHARGE,
				new PaymentCharge("toss-pay-001", "toss"), 10000L, 0L, 10000L, "toss-pay-001");

			StepVerifier.create(
				creditTxAdapter.save(tx)
					.then(creditTxRepo.findById(tx.transactionId().value()))
					.map(CreditTransactionDocument::toDomain))
				.assertNext(restored -> {
					assertThat(restored.source().sourceType()).isEqualTo(CreditSourceType.PAYMENT_CHARGE);
					PaymentCharge src = (PaymentCharge) restored.source();
					assertThat(src.paymentId()).isEqualTo("toss-pay-001");
					assertThat(src.pgProvider()).isEqualTo("toss");
				})
				.verifyComplete();
		}
	}
}
