package com.miyou.app.application.credit.service

import com.miyou.app.application.credit.port.CreditTransactionRepository
import com.miyou.app.domain.credit.exception.InsufficientCreditException
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
@DisplayName("CreditApplicationService")
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
    @DisplayName("getBalance returns an existing credit record")
    fun getBalance_returnsExistingCreditRecord() {
        val userId = UserIdFixture.create()
        val credit = UserCreditFixture.create(userId, 3000L)

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(credit))

        StepVerifier
            .create(service.getBalance(userId))
            .assertNext { result ->
                assertThat(result.userId).isEqualTo(userId)
                assertThat(result.balance).isEqualTo(3000L)
            }.verifyComplete()
    }

    @Test
    @DisplayName("getBalance returns a zero balance when no record exists")
    fun getBalance_returnsZeroBalanceWhenMissing() {
        val userId = UserIdFixture.create()

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty())

        StepVerifier
            .create(service.getBalance(userId))
            .assertNext { result ->
                assertThat(result.userId).isEqualTo(userId)
                assertThat(result.balance).isZero()
            }.verifyComplete()

        verify(userCreditRepository, never()).save(anyValue())
    }

    @Test
    @DisplayName("getTransactions delegates to the repository")
    fun getTransactions_delegatesToRepository() {
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

        StepVerifier
            .create(service.getTransactions(userId, pageable))
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    @DisplayName("deductForConversation saves the updated credit and transaction")
    fun deductForConversation_savesUpdatedCreditAndTransaction() {
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

        StepVerifier
            .create(service.deductForConversation(userId, sessionId))
            .assertNext { result ->
                assertThat(result.type).isEqualTo(CreditTransactionType.DEDUCT)
                assertThat(result.amount).isEqualTo(100L)
            }.verifyComplete()

        assertThat(savedTransaction).isNotNull
        assertThat(savedTransaction!!.referenceId).isEqualTo("session-123")
        assertThat(savedTransaction!!.balanceBefore).isEqualTo(5000L)
        assertThat(savedTransaction!!.balanceAfter).isEqualTo(4900L)
    }

    @Test
    @DisplayName("deductForConversation fails when credit is insufficient")
    fun deductForConversation_failsWhenCreditIsInsufficient() {
        val userId = UserIdFixture.create()

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(UserCreditFixture.create(userId, 50L)))

        StepVerifier
            .create(service.deductForConversation(userId, ConversationSessionFixture.createId()))
            .expectError(InsufficientCreditException::class.java)
            .verify()

        verify(userCreditRepository, never()).save(anyValue())
        verify(creditTransactionRepository, never()).save(anyValue())
    }

    @Test
    @DisplayName("chargeByPayment adds the payment amount to the balance")
    fun chargeByPayment_addsPaymentAmountToBalance() {
        val userId = UserIdFixture.create()
        val existing = UserCreditFixture.create(userId, 1000L)
        val updated = existing.charge(3000L)
        val source = PaymentCharge("payment-123", "toss")

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.just(existing))
        `when`(userCreditRepository.save(updated)).thenReturn(Mono.just(updated))
        `when`(creditTransactionRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<CreditTransaction>(0)) }

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
    @DisplayName("initializeIfAbsent grants the signup bonus when no credit record exists")
    fun initializeIfAbsent_grantsSignupBonusWhenMissing() {
        val userId = UserIdFixture.create()

        `when`(userCreditRepository.findByUserId(userId)).thenReturn(Mono.empty())
        `when`(userCreditRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<UserCredit>(0)) }
        `when`(creditTransactionRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<CreditTransaction>(0)) }

        StepVerifier.create(service.initializeIfAbsent(userId)).verifyComplete()

        verify(userCreditRepository, times(1)).save(anyValue())
        verify(creditTransactionRepository, times(1)).save(anyValue())
    }
}
