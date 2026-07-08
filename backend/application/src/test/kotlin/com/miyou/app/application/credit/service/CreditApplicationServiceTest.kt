package com.miyou.app.application.credit.service

import com.miyou.app.application.credit.port.CreditTransactionRepository
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.credit.exception.UserCreditNotFoundException
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.domain.credit.port.UserCreditRepository
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.fixture.UserCreditFixture
import com.miyou.app.fixture.UserIdFixture
import com.miyou.app.support.anyValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
@DisplayName("크레딧 애플리케이션 서비스")
class CreditApplicationServiceTest {
    @Mock
    private lateinit var userCreditRepository: UserCreditRepository

    @Mock
    private lateinit var creditTransactionRepository: CreditTransactionRepository

    private lateinit var service: CreditApplicationService

    @BeforeEach
    fun setUp() {
        service = CreditApplicationService(userCreditRepository, creditTransactionRepository, 100L, 5000L)
    }

    @Test
    @DisplayName("존재하는 크레딧 기록이 있는 경우 잔액 정보를 반환한다")
    fun getBalance_returnsExistingCreditRecord() {
        // given
        val userId = UserIdFixture.create()
        val credit = UserCreditFixture.create(userId, 3000L)

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(credit))

        // when & then
        StepVerifier
            .create(service.getBalance(userId))
            .assertNext { result ->
                assertThat(result.userId).isEqualTo(userId)
                assertThat(result.balance).isEqualTo(3000L)
            }.verifyComplete()
    }

    @Test
    @DisplayName("크레딧 기록이 없는 경우 잔액이 0인 기본 크레딧 정보를 반환한다")
    fun getBalance_returnsZeroBalanceWhenMissing() {
        // given
        val userId = UserIdFixture.create()

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty())

        // when & then
        StepVerifier
            .create(service.getBalance(userId))
            .assertNext { result ->
                assertThat(result.userId).isEqualTo(userId)
                assertThat(result.balance).isZero()
            }.verifyComplete()

        // 별도의 저장 동작은 발생하지 않아야 함
        verify(userCreditRepository, never()).save(anyValue())
    }

    @Test
    @DisplayName("거래 내역 조회를 리포지토리에 위임한다")
    fun getTransactions_delegatesToRepository() {
        // given
        val userId = UserIdFixture.create()
        val pageable = PageRequest.of(0, 20)
        val transactions =
            Flux.just(
                CreditTransaction.of(
                    userId,
                    CreditTransactionType.CHARGE,
                    PaymentCharge("payment-1", "toss"),
                    1000L,
                    0L,
                    1000L,
                    "payment-1",
                ),
            )

        `when`(creditTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(transactions)

        // when & then
        StepVerifier
            .create(service.getTransactions(userId, pageable))
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    @DisplayName("대화 시작 시 크레딧을 차감하고 업데이트된 크레딧 및 거래 내역을 저장한다")
    fun deductForConversation_savesUpdatedCreditAndTransaction() {
        // given
        val userId = UserIdFixture.create()
        val sessionId = ConversationSessionFixture.createId("session-123")
        val existing = UserCreditFixture.create(userId, 5000L)
        val updated = existing.deduct(100L)
        var savedTransaction: CreditTransaction? = null

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing))
        `when`(userCreditRepository.save(updated)).thenReturn(Mono.just(updated))
        `when`(creditTransactionRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock ->
                val transaction = invocation.getArgument<CreditTransaction>(0)
                savedTransaction = transaction
                Mono.just(transaction)
            }

        // when & then
        StepVerifier
            .create(service.deductForConversation(userId, sessionId.value))
            .assertNext { result ->
                assertThat(result.type).isEqualTo(CreditTransactionType.DEDUCT)
                assertThat(result.amount).isEqualTo(100L)
            }.verifyComplete()

        // 거래 상세 정보 검증
        assertThat(savedTransaction).isNotNull
        assertThat(savedTransaction!!.referenceId).isEqualTo("session-123")
        assertThat(savedTransaction!!.balanceBefore).isEqualTo(5000L)
        assertThat(savedTransaction!!.balanceAfter).isEqualTo(4900L)
    }

    @Test
    @DisplayName("크레딧이 부족할 경우 대화 비용 차감에 실패하고 InsufficientCreditException을 던진다")
    fun deductForConversation_failsWhenCreditIsInsufficient() {
        // given: 50크레딧만 보유하여 차감 기준(100크레딧)보다 부족한 상태
        val userId = UserIdFixture.create()

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(UserCreditFixture.create(userId, 50L)))

        // when & then
        StepVerifier
            .create(service.deductForConversation(userId, ConversationSessionFixture.createId().value))
            .expectError(InsufficientCreditException::class.java)
            .verify()

        // 잔액 부족 시 저장 동작이 차단되어야 함
        verify(userCreditRepository, never()).save(anyValue())
        verify(creditTransactionRepository, never()).save(anyValue())
    }

    @Test
    @DisplayName("대화 오류 시 대화 비용 크레딧을 환불하고 거래 내역을 저장한다")
    fun refundForConversation_restoresConversationCost() {
        // given
        val userId = UserIdFixture.create()
        val sessionId = ConversationSessionFixture.createId("session-123")
        val existing = UserCreditFixture.create(userId, 4900L)
        val updated = existing.charge(100L)
        var savedTransaction: CreditTransaction? = null

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing))
        `when`(userCreditRepository.save(updated)).thenReturn(Mono.just(updated))
        `when`(creditTransactionRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock ->
                val transaction = invocation.getArgument<CreditTransaction>(0)
                savedTransaction = transaction
                Mono.just(transaction)
            }

        // when & then
        StepVerifier
            .create(service.refundForConversation(userId, sessionId.value))
            .assertNext { result ->
                assertThat(result.type).isEqualTo(CreditTransactionType.REFUND)
                assertThat(result.amount).isEqualTo(100L)
            }.verifyComplete()

        // 환불 거래 내역 상세 검증
        assertThat(savedTransaction).isNotNull
        assertThat(savedTransaction!!.referenceId).isEqualTo("session-123")
        assertThat(savedTransaction!!.balanceBefore).isEqualTo(4900L)
        assertThat(savedTransaction!!.balanceAfter).isEqualTo(5000L)
    }

    @Test
    @DisplayName("크레딧 기록이 존재하지 않는 경우 환불에 실패하고 UserCreditNotFoundException을 던진다")
    fun refundForConversation_failsWhenCreditRecordIsMissing() {
        // given
        val userId = UserIdFixture.create()
        val sessionId = ConversationSessionFixture.createId("session-123")

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty())

        // when & then
        StepVerifier
            .create(service.refundForConversation(userId, sessionId.value))
            .expectErrorSatisfies { error ->
                assertThat(error)
                    .isInstanceOf(UserCreditNotFoundException::class.java)
                    .hasMessageContaining("사용자 크레딧 기록을 찾을 수 없습니다.")
            }.verify()

        verify(userCreditRepository, never()).save(anyValue())
        verify(creditTransactionRepository, never()).save(anyValue())
    }

    @Test
    @DisplayName("결제 충전 시 충전 금액만큼 잔액을 추가하고 거래 내역을 저장한다")
    fun chargeByPayment_addsPaymentAmountToBalance() {
        // given
        val userId = UserIdFixture.create()
        val existing = UserCreditFixture.create(userId, 1000L)
        val updated = existing.charge(3000L)
        val source = PaymentCharge("payment-123", "toss")

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing))
        `when`(userCreditRepository.save(updated)).thenReturn(Mono.just(updated))
        `when`(creditTransactionRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<CreditTransaction>(0)) }

        // when & then
        StepVerifier
            .create(service.chargeByPayment(userId, 3000L, source))
            .assertNext { result ->
                assertThat(result.type).isEqualTo(CreditTransactionType.CHARGE)
                assertThat(result.balanceBefore).isEqualTo(1000L)
                assertThat(result.balanceAfter).isEqualTo(4000L)
                assertThat(result.referenceId).isEqualTo("payment-123")
            }.verifyComplete()
    }

    @Test
    @DisplayName("크레딧 기록이 없는 신규 사용자에 대해 가입 보너스를 부여하며 크레딧을 초기화한다")
    fun initializeIfAbsent_grantsSignupBonusWhenMissing() {
        // given
        val userId = UserIdFixture.create()

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty())
        `when`(userCreditRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<UserCredit>(0)) }
        `when`(creditTransactionRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<CreditTransaction>(0)) }

        // when & then
        StepVerifier.create(service.initializeIfAbsent(userId)).verifyComplete()

        // 신규 생성이므로 저장 메소드가 정상 실행되어야 함
        verify(userCreditRepository, times(1)).save(anyValue())
        verify(creditTransactionRepository, times(1)).save(anyValue())
    }
}
