package com.study.webflux.rag.application.credit;

import com.study.webflux.rag.application.credit.service.CreditApplicationService;
import com.study.webflux.rag.domain.credit.exception.InsufficientCreditException;
import com.study.webflux.rag.domain.credit.model.CreditSourceType;
import com.study.webflux.rag.domain.credit.model.CreditTransactionType;
import com.study.webflux.rag.domain.credit.model.source.PaymentCharge;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.infrastructure.credit.adapter.CreditTransactionMongoAdapter;
import com.study.webflux.rag.infrastructure.credit.adapter.UserCreditMongoAdapter;
import com.study.webflux.rag.infrastructure.credit.repository.CreditTransactionMongoRepository;
import com.study.webflux.rag.infrastructure.credit.repository.UserCreditMongoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 크레딧 시스템 통합 테스트 — Application Service + MongoDB Adapter + 실제 DB 연결.
 *
 * 검증 항목:
 * 1. 가입 보너스 지급 → 잔액 5000, SIGNUP_BONUS 트랜잭션 1건
 * 2. 연속 차감 → 잔액 올바르게 감소, 각 DEDUCT 트랜잭션 기록
 * 3. 잔액 부족 시 차감 거부 → 잔액과 트랜잭션 수 불변
 * 4. 결제 충전 → 잔액 증가, PAYMENT_CHARGE 트랜잭션 기록
 * 5. initializeIfAbsent 멱등성 → 중복 가입 보너스 방지
 * 6. 트랜잭션 격리 → 서로 다른 userId는 독립적으로 관리됨
 */
@DataMongoTest
@ActiveProfiles("test")
@Import({
	CreditApplicationService.class,
	UserCreditMongoAdapter.class,
	CreditTransactionMongoAdapter.class
})
@TestPropertySource(properties = {
	"credit.conversation-cost=100",
	"credit.signup-bonus=5000"
})
@DisplayName("[통합] 크레딧 시스템 Application + MongoDB")
class CreditSystemIntegrationTest {

	@Autowired
	private CreditApplicationService creditService;

	@Autowired
	private UserCreditMongoRepository userCreditRepo;

	@Autowired
	private CreditTransactionMongoRepository creditTxRepo;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		userCreditRepo.deleteAll().block();
		creditTxRepo.deleteAll().block();
	}

	// ── 1. 가입 보너스 ─────────────────────────────────────────────────────

	@Nested
	@DisplayName("가입 보너스 지급")
	class SignupBonusFlow {

		@Test
		@DisplayName("신규 유저에게 5000 크레딧 가입 보너스가 지급되고 트랜잭션이 기록된다")
		void grantSignupBonus_newUser_5000CreditsAndTransactionRecorded() {
			UserId userId = UserId.of("signup-user-1");

			StepVerifier.create(creditService.grantSignupBonus(userId))
				.assertNext(tx -> {
					assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE);
					assertThat(tx.amount()).isEqualTo(5000L);
					assertThat(tx.balanceBefore()).isEqualTo(0L);
					assertThat(tx.balanceAfter()).isEqualTo(5000L);
					assertThat(tx.source().sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS);
				})
				.verifyComplete();

			// 잔액 확인
			StepVerifier.create(creditService.getBalance(userId))
				.assertNext(credit -> assertThat(credit.balance()).isEqualTo(5000L))
				.verifyComplete();

			// 트랜잭션 1건 확인
			StepVerifier.create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
				.expectNextCount(1)
				.verifyComplete();
		}
	}

	// ── 2. 연속 차감 ───────────────────────────────────────────────────────

	@Nested
	@DisplayName("연속 대화 차감")
	class ConsecutiveDeductionFlow {

		@Test
		@DisplayName("3번 연속 차감 후 잔액이 4700이 되고 트랜잭션 4건(보너스+3차감)이 기록된다")
		void consecutiveDeductions_balanceDecreasesCorrectly() {
			UserId userId = UserId.of("deduct-user-1");
			ConversationSessionId s1 = ConversationSessionId.of("sess-1");
			ConversationSessionId s2 = ConversationSessionId.of("sess-2");
			ConversationSessionId s3 = ConversationSessionId.of("sess-3");

			StepVerifier.create(
				creditService.grantSignupBonus(userId)
					.then(creditService.deductForConversation(userId, s1))
					.then(creditService.deductForConversation(userId, s2))
					.then(creditService.deductForConversation(userId, s3))
					.then(creditService.getBalance(userId)))
				.assertNext(credit -> assertThat(credit.balance()).isEqualTo(4700L))
				.verifyComplete();

			StepVerifier.create(
				creditService.getTransactions(userId, PageRequest.of(0, 10)))
				.expectNextCount(4)  // SIGNUP + 3 × DEDUCT
				.verifyComplete();
		}

		@Test
		@DisplayName("각 차감 트랜잭션의 balanceBefore/After가 연속적으로 이어진다")
		void consecutiveDeductions_balanceChainIsConsistent() {
			UserId userId = UserId.of("deduct-chain-user");
			ConversationSessionId s1 = ConversationSessionId.of("chain-sess-1");
			ConversationSessionId s2 = ConversationSessionId.of("chain-sess-2");

			StepVerifier.create(
				creditService.grantSignupBonus(userId)
					.then(creditService.deductForConversation(userId, s1))
					.then(creditService.deductForConversation(userId, s2))
					.thenMany(creditService.getTransactions(userId, PageRequest.of(0, 10))))
				.assertNext(tx -> {
					// 최신 차감 (두 번째 차감)
					assertThat(tx.type()).isEqualTo(CreditTransactionType.DEDUCT);
					assertThat(tx.balanceBefore()).isEqualTo(4900L);
					assertThat(tx.balanceAfter()).isEqualTo(4800L);
				})
				.assertNext(tx -> {
					// 첫 번째 차감
					assertThat(tx.type()).isEqualTo(CreditTransactionType.DEDUCT);
					assertThat(tx.balanceBefore()).isEqualTo(5000L);
					assertThat(tx.balanceAfter()).isEqualTo(4900L);
				})
				.assertNext(tx -> assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE))
				.verifyComplete();
		}
	}

	// ── 3. 잔액 부족 차감 거부 ─────────────────────────────────────────────

	@Nested
	@DisplayName("잔액 부족 시 차감 거부")
	class InsufficientCreditFlow {

		@Test
		@DisplayName("잔액 50에서 100 차감 시도 시 예외 발생, 잔액과 트랜잭션 수가 변하지 않는다")
		void deduct_insufficient_balanceAndTxCountUnchanged() {
			UserId userId = UserId.of("insufficient-user-1");

			// 가입 보너스 지급 후 대부분을 결제로 소진시킨 상황을 모사:
			// grantSignupBonus → 5000, 그 다음 49번 deduct → 100 남기고 → 50으로 만들기 위해 직접 저장
			// 실제 테스트는 단순히 잔액 50 상태에서 시작
			com.study.webflux.rag.domain.credit.model.UserCredit lowCredit =
				new com.study.webflux.rag.domain.credit.model.UserCredit(userId, 50L, 0L);

			StepVerifier.create(
				userCreditRepo.save(
					com.study.webflux.rag.infrastructure.credit.document.UserCreditDocument.fromDomain(
						lowCredit))
					.then(creditService.deductForConversation(userId, ConversationSessionId.of("s-fail"))))
				.expectError(InsufficientCreditException.class)
				.verify();

			// 잔액 불변 확인
			StepVerifier.create(creditService.getBalance(userId))
				.assertNext(credit -> assertThat(credit.balance()).isEqualTo(50L))
				.verifyComplete();

			// 트랜잭션 추가 없음 확인
			StepVerifier.create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
				.verifyComplete();
		}

		@Test
		@DisplayName("잔액이 정확히 99일 때 100 차감 시도 시 거부된다")
		void deduct_balanceIs99_rejected() {
			UserId userId = UserId.of("boundary-user");
			com.study.webflux.rag.domain.credit.model.UserCredit nearThreshold =
				new com.study.webflux.rag.domain.credit.model.UserCredit(userId, 99L, 0L);

			StepVerifier.create(
				userCreditRepo.save(
					com.study.webflux.rag.infrastructure.credit.document.UserCreditDocument.fromDomain(
						nearThreshold))
					.then(creditService.deductForConversation(userId, ConversationSessionId.of("s-boundary"))))
				.expectError(InsufficientCreditException.class)
				.verify();
		}
	}

	// ── 4. PG 결제 충전 ────────────────────────────────────────────────────

	@Nested
	@DisplayName("결제 충전")
	class PaymentChargeFlow {

		@Test
		@DisplayName("결제 충전 후 잔액이 증가하고 PAYMENT_CHARGE 트랜잭션이 기록된다")
		void chargeByPayment_increasesBalanceAndRecordsTx() {
			UserId userId = UserId.of("payment-user-1");
			PaymentCharge source = new PaymentCharge("toss-pay-abc", "toss");

			StepVerifier.create(
				creditService.grantSignupBonus(userId)
					.then(creditService.chargeByPayment(userId, 10000L, source))
					.then(creditService.getBalance(userId)))
				.assertNext(credit -> assertThat(credit.balance()).isEqualTo(15000L))
				.verifyComplete();

			StepVerifier.create(
				creditService.getTransactions(userId, PageRequest.of(0, 10)))
				.assertNext(tx -> {
					assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE);
					assertThat(tx.source().sourceType()).isEqualTo(CreditSourceType.PAYMENT_CHARGE);
					assertThat(tx.amount()).isEqualTo(10000L);
					assertThat(tx.referenceId()).isEqualTo("toss-pay-abc");
				})
				.assertNext(tx -> assertThat(tx.source().sourceType())
					.isEqualTo(CreditSourceType.SIGNUP_BONUS))
				.verifyComplete();
		}
	}

	// ── 5. initializeIfAbsent 멱등성 ──────────────────────────────────────

	@Nested
	@DisplayName("initializeIfAbsent 멱등성")
	class InitializeIfAbsentIdempotency {

		@Test
		@DisplayName("initializeIfAbsent 두 번 호출해도 가입 보너스가 한 번만 지급된다")
		void initializeIfAbsent_calledTwice_bonusGrantedOnce() {
			UserId userId = UserId.of("idempotent-user-1");

			StepVerifier.create(
				creditService.initializeIfAbsent(userId)
					.then(creditService.initializeIfAbsent(userId))
					.then(creditService.getBalance(userId)))
				.assertNext(credit -> assertThat(credit.balance()).isEqualTo(5000L))
				.verifyComplete();

			StepVerifier.create(
				creditService.getTransactions(userId, PageRequest.of(0, 10)))
				.expectNextCount(1)  // SIGNUP_BONUS 딱 1건
				.verifyComplete();
		}

		@Test
		@DisplayName("이미 크레딧이 있는 유저에게 initializeIfAbsent 호출해도 잔액이 변하지 않는다")
		void initializeIfAbsent_existingUser_noChange() {
			UserId userId = UserId.of("idempotent-existing-user");

			StepVerifier.create(
				creditService.grantSignupBonus(userId)
					.then(creditService.deductForConversation(userId, ConversationSessionId.of("s-x")))
					.then(creditService.initializeIfAbsent(userId))
					.then(creditService.getBalance(userId)))
				.assertNext(credit -> assertThat(credit.balance()).isEqualTo(4900L))
				.verifyComplete();
		}
	}

	// ── 6. 유저 간 트랜잭션 격리 ──────────────────────────────────────────

	@Nested
	@DisplayName("유저 간 격리")
	class UserIsolation {

		@Test
		@DisplayName("유저 A의 차감이 유저 B의 잔액에 영향을 주지 않는다")
		void deductUserA_doesNotAffectUserB() {
			UserId userA = UserId.of("isolation-user-a");
			UserId userB = UserId.of("isolation-user-b");

			StepVerifier.create(
				creditService.grantSignupBonus(userA)
					.then(creditService.grantSignupBonus(userB))
					.then(creditService.deductForConversation(userA, ConversationSessionId.of("s-a-1")))
					.then(creditService.deductForConversation(userA, ConversationSessionId.of("s-a-2")))
					.then(creditService.getBalance(userB)))
				.assertNext(credit -> assertThat(credit.balance()).isEqualTo(5000L))
				.verifyComplete();
		}

		@Test
		@DisplayName("두 유저의 트랜잭션이 서로 섞이지 않는다")
		void transactions_areIsolatedPerUser() {
			UserId userA = UserId.of("tx-isolation-a");
			UserId userB = UserId.of("tx-isolation-b");

			StepVerifier.create(
				creditService.grantSignupBonus(userA)
					.then(creditService.grantSignupBonus(userB))
					.then(creditService.deductForConversation(userA, ConversationSessionId.of("s-a")))
					.thenMany(creditService.getTransactions(userA, PageRequest.of(0, 10))))
				.assertNext(tx -> assertThat(tx.userId()).isEqualTo(userA))
				.assertNext(tx -> assertThat(tx.userId()).isEqualTo(userA))
				.verifyComplete();
		}
	}

	// ── 7. 동시 요청 시나리오 (낙관적 동시성) ─────────────────────────────

	@Nested
	@DisplayName("동시 차감 시나리오")
	class ConcurrentDeduction {

		@Test
		@DisplayName("동시에 50번 차감 요청 시 성공한 요청만큼만 잔액이 감소한다 (잔액은 0 이상)")
		void concurrent_deductions_balanceNeverGoesNegative() {
			UserId userId = UserId.of("concurrent-user-1");
			// 가입 보너스 5000, 100 × 50 = 5000 → 정확히 맞음
			creditService.grantSignupBonus(userId).block();

			int concurrentRequests = 50;
			Flux<Long> deductions = Flux.range(0, concurrentRequests)
				.flatMap(i -> creditService
					.deductForConversation(userId, ConversationSessionId.of("concurrent-s-" + i))
					.thenReturn(1L)
					.onErrorReturn(0L));  // InsufficientCreditException 발생 시 0 반환

			long succeeded = deductions.collectList().block()
				.stream().mapToLong(Long::longValue).sum();

			// 잔액이 절대 음수가 되지 않음
			StepVerifier.create(creditService.getBalance(userId))
				.assertNext(credit -> assertThat(credit.balance()).isGreaterThanOrEqualTo(0L))
				.verifyComplete();

			// 성공한 요청 수 × 100 = 차감된 총액
			long finalBalance = creditService.getBalance(userId).block().balance();
			assertThat(5000L - finalBalance).isEqualTo(succeeded * 100L);
		}
	}
}
