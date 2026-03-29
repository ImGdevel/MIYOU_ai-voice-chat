package com.miyou.app.application.credit.service;

import com.miyou.app.application.credit.port.CreditTransactionRepository;
import com.miyou.app.domain.credit.exception.InsufficientCreditException;
import com.miyou.app.domain.credit.model.CreditTransaction;
import com.miyou.app.domain.credit.model.CreditTransactionType;
import com.miyou.app.domain.credit.model.PaymentCharge;
import com.miyou.app.domain.credit.model.UserCredit;
import com.miyou.app.domain.credit.port.UserCreditRepository;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.fixture.ConversationSessionFixture;
import com.miyou.app.fixture.MissionFixture;
import com.miyou.app.fixture.UserCreditFixture;
import com.miyou.app.fixture.UserIdFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditApplicationService")
class CreditApplicationServiceTest {

	@Mock
	private UserCreditRepository userCreditRepository;

	@Mock
	private CreditTransactionRepository creditTransactionRepository;

	private CreditApplicationService service;

	private static final long CONVERSATION_COST = 100L;
	private static final long SIGNUP_BONUS = 5000L;

	@BeforeEach
	void setUp() {
		service = new CreditApplicationService(
			userCreditRepository,
			creditTransactionRepository,
			CONVERSATION_COST,
			SIGNUP_BONUS);
	}

	// ── getBalance ─────────────────────────────────────────────────────────

	@Nested
	@DisplayName("getBalance()")
	class GetBalance {

		@Test
		@DisplayName("크레딧 레코드가 있으면 현재 잔액을 반환한다")
		void getBalance_existing_returnsCredit() {
			UserId userId = UserIdFixture.create();
			UserCredit expected = UserCreditFixture.create(userId, 3000L);
			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(expected));

			StepVerifier.create(service.getBalance(userId))
				.assertNext(credit -> {
					assertThat(credit.userId()).isEqualTo(userId);
					assertThat(credit.balance()).isEqualTo(3000L);
				})
				.verifyComplete();
		}

		@Test
		@DisplayName("크레딧 레코드가 없으면 잔액 0의 기본값을 반환한다 (저장하지 않음)")
		void getBalance_notFound_returnsZeroDefault() {
			UserId userId = UserIdFixture.create();
			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty());

			StepVerifier.create(service.getBalance(userId))
				.assertNext(credit -> {
					assertThat(credit.balance()).isZero();
					assertThat(credit.userId()).isEqualTo(userId);
				})
				.verifyComplete();

			verify(userCreditRepository, never()).save(any());
		}
	}

	// ── getTransactions ────────────────────────────────────────────────────

	@Nested
	@DisplayName("getTransactions()")
	class GetTransactions {

		@Test
		@DisplayName("userId의 트랜잭션 목록을 최신순으로 반환한다")
		void getTransactions_returnsSortedList() {
			UserId userId = UserIdFixture.create();
			CreditTransaction tx1 = com.miyou.app.fixture.CreditTransactionFixture
				.deduction(userId, 5000L);
			CreditTransaction tx2 = com.miyou.app.fixture.CreditTransactionFixture
				.signupBonus(userId, SIGNUP_BONUS);

			when(creditTransactionRepository.findByUserIdOrderByCreatedAtDesc(
				any(UserId.class),
				any(org.springframework.data.domain.Pageable.class)))
				.thenReturn(Flux.just(tx1, tx2));

			StepVerifier.create(service.getTransactions(userId,
				org.springframework.data.domain.PageRequest.of(0, 20)))
				.expectNextCount(2)
				.verifyComplete();
		}
	}

	// ── deductForConversation ──────────────────────────────────────────────

	@Nested
	@DisplayName("deductForConversation()")
	class DeductForConversation {

		@Test
		@DisplayName("잔액 충분 시 100 크레딧 차감 후 CreditTransaction을 반환한다")
		void deduct_sufficient_returnsTransaction() {
			UserId userId = UserIdFixture.create();
			UserCredit existing = UserCreditFixture.create(userId, 5000L);
			UserCredit updated = existing.deduct(CONVERSATION_COST);
			CreditTransaction savedTx = com.miyou.app.fixture.CreditTransactionFixture
				.deduction(userId, 5000L);

			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing));
			when(userCreditRepository.save(any())).thenReturn(Mono.just(updated));
			when(creditTransactionRepository.save(any())).thenReturn(Mono.just(savedTx));

			StepVerifier.create(service.deductForConversation(
				userId,
				ConversationSessionFixture.createId()))
				.assertNext(tx -> {
					assertThat(tx.type()).isEqualTo(CreditTransactionType.DEDUCT);
					assertThat(tx.amount()).isEqualTo(CONVERSATION_COST);
				})
				.verifyComplete();
		}

		@Test
		@DisplayName("잔액 부족 시 InsufficientCreditException 발생, 저장은 호출되지 않는다")
		void deduct_insufficient_throwsWithoutSave() {
			UserId userId = UserIdFixture.create();
			UserCredit lowCredit = UserCreditFixture.create(userId, 50L);
			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(lowCredit));

			StepVerifier.create(service.deductForConversation(
				userId,
				ConversationSessionFixture.createId()))
				.expectError(InsufficientCreditException.class)
				.verify();

			verify(userCreditRepository, never()).save(any());
			verify(creditTransactionRepository, never()).save(any());
		}

		@Test
		@DisplayName("크레딧 레코드가 없으면 InsufficientCreditException 발생")
		void deduct_noCreditRecord_throws() {
			UserId userId = UserIdFixture.create();
			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty());

			StepVerifier.create(service.deductForConversation(
				userId,
				ConversationSessionFixture.createId()))
				.expectError(InsufficientCreditException.class)
				.verify();
		}

		@Test
		@DisplayName("차감 시 저장되는 CreditTransaction에 sessionId가 referenceId로 설정된다")
		void deduct_capturesSavedTransaction_withReferenceId() {
			UserId userId = UserIdFixture.create();
			var sessionId = ConversationSessionFixture.createId("my-session-id");
			UserCredit existing = UserCreditFixture.create(userId, 5000L);
			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing));
			when(userCreditRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

			ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor
				.forClass(CreditTransaction.class);
			when(creditTransactionRepository.save(txCaptor.capture()))
				.thenAnswer(inv -> Mono.just(inv.getArgument(0)));

			StepVerifier.create(service.deductForConversation(userId, sessionId))
				.expectNextCount(1)
				.verifyComplete();

			CreditTransaction captured = txCaptor.getValue();
			assertThat(captured.referenceId()).isEqualTo("my-session-id");
			assertThat(captured.balanceBefore()).isEqualTo(5000L);
			assertThat(captured.balanceAfter()).isEqualTo(4900L);
		}
	}

	// ── grantSignupBonus ───────────────────────────────────────────────────

	@Nested
	@DisplayName("grantSignupBonus()")
	class GrantSignupBonus {

		@Test
		@DisplayName("가입 보너스 지급 시 5000 크레딧 CHARGE 트랜잭션이 생성된다")
		void grantSignupBonus_createsChargeTransaction() {
			UserId userId = UserIdFixture.create();
			ArgumentCaptor<UserCredit> creditCaptor = ArgumentCaptor.forClass(UserCredit.class);
			ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor
				.forClass(CreditTransaction.class);

			when(userCreditRepository.save(creditCaptor.capture()))
				.thenAnswer(inv -> Mono.just(inv.getArgument(0)));
			when(creditTransactionRepository.save(txCaptor.capture()))
				.thenAnswer(inv -> Mono.just(inv.getArgument(0)));

			StepVerifier.create(service.grantSignupBonus(userId))
				.assertNext(tx -> {
					assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE);
					assertThat(tx.amount()).isEqualTo(SIGNUP_BONUS);
					assertThat(tx.balanceBefore()).isEqualTo(0L);
					assertThat(tx.balanceAfter()).isEqualTo(SIGNUP_BONUS);
				})
				.verifyComplete();

			assertThat(creditCaptor.getValue().balance()).isEqualTo(SIGNUP_BONUS);
		}
	}

	// ── chargeByPayment ────────────────────────────────────────────────────

	@Nested
	@DisplayName("chargeByPayment()")
	class ChargeByPayment {

		@Test
		@DisplayName("기존 잔액에 결제 금액이 추가된다")
		void chargeByPayment_addsToExistingBalance() {
			UserId userId = UserIdFixture.create();
			UserCredit existing = UserCreditFixture.create(userId, 1000L);
			PaymentCharge source = new PaymentCharge("pay-001", "toss");

			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing));
			when(userCreditRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
			when(creditTransactionRepository.save(any()))
				.thenAnswer(inv -> Mono.just(inv.getArgument(0)));

			StepVerifier.create(service.chargeByPayment(userId, 3000L, source))
				.assertNext(tx -> {
					assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE);
					assertThat(tx.amount()).isEqualTo(3000L);
					assertThat(tx.balanceBefore()).isEqualTo(1000L);
					assertThat(tx.balanceAfter()).isEqualTo(4000L);
					assertThat(tx.referenceId()).isEqualTo("pay-001");
				})
				.verifyComplete();
		}

		@Test
		@DisplayName("크레딧 레코드가 없는 신규 유저도 결제 충전이 가능하다")
		void chargeByPayment_newUser_startsFromZero() {
			UserId userId = UserIdFixture.create();
			PaymentCharge source = new PaymentCharge("pay-002", "kakao");

			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty());
			when(userCreditRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
			when(creditTransactionRepository.save(any()))
				.thenAnswer(inv -> Mono.just(inv.getArgument(0)));

			StepVerifier.create(service.chargeByPayment(userId, 10000L, source))
				.assertNext(tx -> {
					assertThat(tx.balanceBefore()).isEqualTo(0L);
					assertThat(tx.balanceAfter()).isEqualTo(10000L);
				})
				.verifyComplete();
		}
	}

	// ── grantMissionReward ─────────────────────────────────────────────────

	@Nested
	@DisplayName("grantMissionReward()")
	class GrantMissionReward {

		@Test
		@DisplayName("미션 보상 지급 시 잔액에 추가되고 MISSION_REWARD 출처의 CHARGE 트랜잭션이 생성된다")
		void grantMissionReward_addsRewardToBalance() {
			UserId userId = UserIdFixture.create();
			UserCredit existing = UserCreditFixture.create(userId, 2000L);
			var missionId = com.miyou.app.domain.mission.model.MissionId.of(
				MissionFixture.DEFAULT_MISSION_ID);

			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing));
			when(userCreditRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
			when(creditTransactionRepository.save(any()))
				.thenAnswer(inv -> Mono.just(inv.getArgument(0)));

			StepVerifier
				.create(service.grantMissionReward(userId, missionId, 500L, "SHARE_SERVICE"))
				.assertNext(tx -> {
					assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE);
					assertThat(tx.amount()).isEqualTo(500L);
					assertThat(tx.balanceBefore()).isEqualTo(2000L);
					assertThat(tx.balanceAfter()).isEqualTo(2500L);
					assertThat(tx.source().sourceType())
						.isEqualTo(
							com.miyou.app.domain.credit.model.CreditSourceType.MISSION_REWARD);
				})
				.verifyComplete();
		}
	}

	// ── initializeIfAbsent ─────────────────────────────────────────────────

	@Nested
	@DisplayName("initializeIfAbsent()")
	class InitializeIfAbsent {

		@Test
		@DisplayName("크레딧 레코드가 없으면 가입 보너스를 지급하고 저장한다")
		void initializeIfAbsent_noRecord_grantsBonus() {
			UserId userId = UserIdFixture.create();
			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty());
			when(userCreditRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
			when(creditTransactionRepository.save(any()))
				.thenAnswer(inv -> Mono.just(inv.getArgument(0)));

			StepVerifier.create(service.initializeIfAbsent(userId))
				.verifyComplete();

			verify(userCreditRepository, times(1)).save(any());
			verify(creditTransactionRepository, times(1)).save(any());
		}

		@Test
		@DisplayName("크레딧 레코드가 이미 있으면 저장을 호출하지 않는다 (멱등성)")
		void initializeIfAbsent_existing_skips() {
			UserId userId = UserIdFixture.create();
			UserCredit existing = UserCreditFixture.create(userId, 5000L);
			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing));

			StepVerifier.create(service.initializeIfAbsent(userId))
				.verifyComplete();

			verify(userCreditRepository, never()).save(any());
			verify(creditTransactionRepository, never()).save(any());
		}

		@Test
		@DisplayName("동시 요청으로 DuplicateKeyException 발생 시 에러를 흡수하고 완료한다")
		void initializeIfAbsent_duplicateKey_absorbs() {
			UserId userId = UserIdFixture.create();
			when(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty());
			when(userCreditRepository.save(any())).thenReturn(
				Mono.error(new org.springframework.dao.DuplicateKeyException("duplicate")));

			StepVerifier.create(service.initializeIfAbsent(userId))
				.verifyComplete();
		}
	}
}
