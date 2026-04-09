package com.miyou.app.application.credit.service

import com.miyou.app.application.credit.port.CreditTransactionRepository
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.credit.model.CreditSourceType
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.domain.credit.port.UserCreditRepository
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.fixture.CreditTransactionFixture
import com.miyou.app.fixture.MissionFixture
import com.miyou.app.fixture.UserCreditFixture
import com.miyou.app.fixture.UserIdFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
@DisplayName("CreditApplicationService")
class CreditApplicationServiceTest {

	@Mock
	private lateinit var userCreditRepository: UserCreditRepository

	@Mock
	private lateinit var creditTransactionRepository: CreditTransactionRepository

	private lateinit var service: CreditApplicationService

	private val conversationCost = 100L
	private val signupBonus = 5000L

	@BeforeEach
	fun setUp() {
		service = CreditApplicationService(
			userCreditRepository,
			creditTransactionRepository,
			conversationCost,
			signupBonus,
		)
	}

	@Nested
	@DisplayName("getBalance()")
	inner class GetBalance {

		@Test
		@DisplayName("크레딧 레코드가 있으면 현재 잔액을 반환한다")
		fun getBalance_existing_returnsCredit() {
			val userId = UserIdFixture.create()
			val expected = UserCreditFixture.create(userId, 3000L)
			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(expected))

			StepVerifier.create(service.getBalance(userId))
				.assertNext { credit ->
					assertThat(credit.userId()).isEqualTo(userId)
					assertThat(credit.balance()).isEqualTo(3000L)
				}
				.verifyComplete()
		}

		@Test
		@DisplayName("크레딧 레코드가 없으면 잔액 0의 기본값을 반환한다 (저장하지 않음)")
		fun getBalance_notFound_returnsZeroDefault() {
			val userId = UserIdFixture.create()
			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty())

			StepVerifier.create(service.getBalance(userId))
				.assertNext { credit ->
					assertThat(credit.balance()).isZero()
					assertThat(credit.userId()).isEqualTo(userId)
				}
				.verifyComplete()

			verify(userCreditRepository, never()).save(any())
		}
	}

	@Nested
	@DisplayName("getTransactions()")
	inner class GetTransactions {

		@Test
		@DisplayName("userId의 트랜잭션 목록을 최신순으로 반환한다")
		fun getTransactions_returnsSortedList() {
			val userId = UserIdFixture.create()
			val tx1 = CreditTransactionFixture.deduction(userId, 5000L)
			val tx2 = CreditTransactionFixture.signupBonus(userId, signupBonus)

			`when`(creditTransactionRepository.findByUserIdOrderByCreatedAtDesc(any(), any()))
				.thenReturn(Flux.just(tx1, tx2))

			StepVerifier.create(service.getTransactions(userId, PageRequest.of(0, 20)))
				.expectNextCount(2)
				.verifyComplete()
		}
	}

	@Nested
	@DisplayName("deductForConversation()")
	inner class DeductForConversation {

		@Test
		@DisplayName("잔액 충분 시 100 크레딧 차감 후 CreditTransaction을 반환한다")
		fun deduct_sufficient_returnsTransaction() {
			val userId = UserIdFixture.create()
			val existing = UserCreditFixture.create(userId, 5000L)
			val updated = existing.deduct(conversationCost)
			val savedTx = CreditTransactionFixture.deduction(userId, 5000L)

			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing))
			`when`(userCreditRepository.save(any())).thenReturn(Mono.just(updated))
			`when`(creditTransactionRepository.save(any())).thenReturn(Mono.just(savedTx))

			StepVerifier.create(service.deductForConversation(userId, ConversationSessionFixture.createId()))
				.assertNext { tx ->
					assertThat(tx.type()).isEqualTo(CreditTransactionType.DEDUCT)
					assertThat(tx.amount()).isEqualTo(conversationCost)
				}
				.verifyComplete()
		}

		@Test
		@DisplayName("잔액 부족 시 InsufficientCreditException 발생, 저장은 호출되지 않는다")
		fun deduct_insufficient_throwsWithoutSave() {
			val userId = UserIdFixture.create()
			val lowCredit = UserCreditFixture.create(userId, 50L)
			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(lowCredit))

			StepVerifier.create(service.deductForConversation(userId, ConversationSessionFixture.createId()))
				.expectError(InsufficientCreditException::class.java)
				.verify()

			verify(userCreditRepository, never()).save(any())
			verify(creditTransactionRepository, never()).save(any())
		}

		@Test
		@DisplayName("크레딧 레코드가 없으면 InsufficientCreditException 발생")
		fun deduct_noCreditRecord_throws() {
			val userId = UserIdFixture.create()
			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty())

			StepVerifier.create(service.deductForConversation(userId, ConversationSessionFixture.createId()))
				.expectError(InsufficientCreditException::class.java)
				.verify()
		}

		@Test
		@DisplayName("차감 시 저장되는 CreditTransaction에 sessionId가 referenceId로 설정된다")
		fun deduct_capturesSavedTransaction_withReferenceId() {
			val userId = UserIdFixture.create()
			val sessionId = ConversationSessionFixture.createId("my-session-id")
			val existing = UserCreditFixture.create(userId, 5000L)
			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing))
			`when`(userCreditRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
				Mono.just(invocation.getArgument<UserCredit>(0))
			}

			val txCaptor = ArgumentCaptor.forClass(CreditTransaction::class.java)
			`when`(creditTransactionRepository.save(txCaptor.capture()))
				.thenAnswer { invocation: InvocationOnMock ->
					Mono.just(invocation.getArgument<CreditTransaction>(0))
				}

			StepVerifier.create(service.deductForConversation(userId, sessionId))
				.expectNextCount(1)
				.verifyComplete()

			val captured = txCaptor.value
			assertThat(captured.referenceId()).isEqualTo("my-session-id")
			assertThat(captured.balanceBefore()).isEqualTo(5000L)
			assertThat(captured.balanceAfter()).isEqualTo(4900L)
		}
	}

	@Nested
	@DisplayName("grantSignupBonus()")
	inner class GrantSignupBonus {

		@Test
		@DisplayName("가입 보너스 지급 시 5000 크레딧 CHARGE 트랜잭션이 생성된다")
		fun grantSignupBonus_createsChargeTransaction() {
			val userId = UserIdFixture.create()
			val creditCaptor = ArgumentCaptor.forClass(UserCredit::class.java)
			val txCaptor = ArgumentCaptor.forClass(CreditTransaction::class.java)

			`when`(userCreditRepository.save(creditCaptor.capture()))
				.thenAnswer { invocation: InvocationOnMock ->
					Mono.just(invocation.getArgument<UserCredit>(0))
				}
			`when`(creditTransactionRepository.save(txCaptor.capture()))
				.thenAnswer { invocation: InvocationOnMock ->
					Mono.just(invocation.getArgument<CreditTransaction>(0))
				}

			StepVerifier.create(service.grantSignupBonus(userId))
				.assertNext { tx ->
					assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE)
					assertThat(tx.amount()).isEqualTo(signupBonus)
					assertThat(tx.balanceBefore()).isEqualTo(0L)
					assertThat(tx.balanceAfter()).isEqualTo(signupBonus)
				}
				.verifyComplete()

			assertThat(creditCaptor.value.balance()).isEqualTo(signupBonus)
		}
	}

	@Nested
	@DisplayName("chargeByPayment()")
	inner class ChargeByPayment {

		@Test
		@DisplayName("기존 잔액에 결제 금액이 추가된다")
		fun chargeByPayment_addsToExistingBalance() {
			val userId = UserIdFixture.create()
			val existing = UserCreditFixture.create(userId, 1000L)
			val source = PaymentCharge("pay-001", "toss")

			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing))
			`when`(userCreditRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
				Mono.just(invocation.getArgument<UserCredit>(0))
			}
			`when`(creditTransactionRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
				Mono.just(invocation.getArgument<CreditTransaction>(0))
			}

			StepVerifier.create(service.chargeByPayment(userId, 3000L, source))
				.assertNext { tx ->
					assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE)
					assertThat(tx.amount()).isEqualTo(3000L)
					assertThat(tx.balanceBefore()).isEqualTo(1000L)
					assertThat(tx.balanceAfter()).isEqualTo(4000L)
					assertThat(tx.referenceId()).isEqualTo("pay-001")
				}
				.verifyComplete()
		}

		@Test
		@DisplayName("크레딧 레코드가 없는 신규 유저도 결제 충전이 가능하다")
		fun chargeByPayment_newUser_startsFromZero() {
			val userId = UserIdFixture.create()
			val source = PaymentCharge("pay-002", "kakao")

			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty())
			`when`(userCreditRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
				Mono.just(invocation.getArgument<UserCredit>(0))
			}
			`when`(creditTransactionRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
				Mono.just(invocation.getArgument<CreditTransaction>(0))
			}

			StepVerifier.create(service.chargeByPayment(userId, 10000L, source))
				.assertNext { tx ->
					assertThat(tx.balanceBefore()).isEqualTo(0L)
					assertThat(tx.balanceAfter()).isEqualTo(10000L)
				}
				.verifyComplete()
		}
	}

	@Nested
	@DisplayName("grantMissionReward()")
	inner class GrantMissionReward {

		@Test
		@DisplayName("미션 보상 지급 시 잔액에 추가되고 MISSION_REWARD 출처의 CHARGE 트랜잭션이 생성된다")
		fun grantMissionReward_addsRewardToBalance() {
			val userId = UserIdFixture.create()
			val existing = UserCreditFixture.create(userId, 2000L)
			val missionId = com.miyou.app.domain.mission.model.MissionId.of(MissionFixture.DEFAULT_MISSION_ID)

			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing))
			`when`(userCreditRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
				Mono.just(invocation.getArgument<UserCredit>(0))
			}
			`when`(creditTransactionRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
				Mono.just(invocation.getArgument<CreditTransaction>(0))
			}

			StepVerifier.create(service.grantMissionReward(userId, missionId, 500L, "SHARE_SERVICE"))
				.assertNext { tx ->
					assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE)
					assertThat(tx.amount()).isEqualTo(500L)
					assertThat(tx.balanceBefore()).isEqualTo(2000L)
					assertThat(tx.balanceAfter()).isEqualTo(2500L)
					assertThat(tx.source().sourceType()).isEqualTo(CreditSourceType.MISSION_REWARD)
				}
				.verifyComplete()
		}
	}

	@Nested
	@DisplayName("initializeIfAbsent()")
	inner class InitializeIfAbsent {

		@Test
		@DisplayName("크레딧 레코드가 없으면 가입 보너스를 지급하고 저장한다")
		fun initializeIfAbsent_noRecord_grantsBonus() {
			val userId = UserIdFixture.create()
			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty())
			`when`(userCreditRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
				Mono.just(invocation.getArgument<UserCredit>(0))
			}
			`when`(creditTransactionRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
				Mono.just(invocation.getArgument<CreditTransaction>(0))
			}

			StepVerifier.create(service.initializeIfAbsent(userId))
				.verifyComplete()

			verify(userCreditRepository, times(1)).save(any())
			verify(creditTransactionRepository, times(1)).save(any())
		}

		@Test
		@DisplayName("크레딧 레코드가 이미 있으면 저장을 호출하지 않는다 (멱등성)")
		fun initializeIfAbsent_existing_skips() {
			val userId = UserIdFixture.create()
			val existing = UserCreditFixture.create(userId, 5000L)
			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing))

			StepVerifier.create(service.initializeIfAbsent(userId))
				.verifyComplete()

			verify(userCreditRepository, never()).save(any())
			verify(creditTransactionRepository, never()).save(any())
		}

		@Test
		@DisplayName("동시 요청으로 DuplicateKeyException 발생 시 에러를 흡수하고 완료한다")
		fun initializeIfAbsent_duplicateKey_absorbs() {
			val userId = UserIdFixture.create()
			`when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty())
			`when`(userCreditRepository.save(any()))
				.thenReturn(Mono.error(org.springframework.dao.DuplicateKeyException("duplicate")))

			StepVerifier.create(service.initializeIfAbsent(userId))
				.verifyComplete()
		}
	}
}
