package com.miyou.app.application.credit

import com.miyou.app.application.credit.service.CreditApplicationService
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.credit.model.CreditSourceType
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.infrastructure.credit.adapter.CreditTransactionMongoAdapter
import com.miyou.app.infrastructure.credit.adapter.UserCreditMongoAdapter
import com.miyou.app.infrastructure.credit.document.UserCreditDocument
import com.miyou.app.infrastructure.credit.repository.CreditTransactionMongoRepository
import com.miyou.app.infrastructure.credit.repository.UserCreditMongoRepository
import com.miyou.app.support.ContainerizedIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

@DataMongoTest
@ActiveProfiles("test")
@Import(
    CreditApplicationService::class,
    UserCreditMongoAdapter::class,
    CreditTransactionMongoAdapter::class,
)
@TestPropertySource(
    properties = [
        "credit.conversation-cost=100",
        "credit.signup-bonus=5000",
    ],
)
@DisplayName("[통합] 크레딧 시스템 Application + MongoDB")
class CreditSystemIntegrationTest : ContainerizedIntegrationTestSupport() {
    @Autowired
    private lateinit var creditService: CreditApplicationService

    @Autowired
    private lateinit var userCreditRepo: UserCreditMongoRepository

    @Autowired
    private lateinit var creditTxRepo: CreditTransactionMongoRepository

    @BeforeEach
    @AfterEach
    fun cleanUp() {
        userCreditRepo.deleteAll().block()
        creditTxRepo.deleteAll().block()
    }

    @Nested
    @DisplayName("가입 보너스 지급")
    inner class SignupBonusFlow {
        @Test
        @DisplayName("신규 유저에게 5000 크레딧 가입 보너스가 지급되고 트랜잭션이 기록된다")
        fun grantSignupBonus_newUser_5000CreditsAndTransactionRecorded() {
            val userId = UserId.of("signup-user-1")

            StepVerifier
                .create(creditService.grantSignupBonus(userId))
                .assertNext { tx ->
                    assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE)
                    assertThat(tx.amount()).isEqualTo(5000L)
                    assertThat(tx.balanceBefore()).isEqualTo(0L)
                    assertThat(tx.balanceAfter()).isEqualTo(5000L)
                    assertThat(tx.source().sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS)
                }.verifyComplete()

            StepVerifier
                .create(creditService.getBalance(userId))
                .assertNext { credit -> assertThat(credit.balance()).isEqualTo(5000L) }
                .verifyComplete()

            StepVerifier
                .create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
                .expectNextCount(1)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("연속 대화 차감")
    inner class ConsecutiveDeductionFlow {
        @Test
        @DisplayName("3번 연속 차감 후 잔액이 4700이 되고 트랜잭션 4건(보너스+3차감)이 기록된다")
        fun consecutiveDeductions_balanceDecreasesCorrectly() {
            val userId = UserId.of("deduct-user-1")
            val session1 = ConversationSessionId.of("sess-1")
            val session2 = ConversationSessionId.of("sess-2")
            val session3 = ConversationSessionId.of("sess-3")

            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userId)
                        .then(creditService.deductForConversation(userId, session1))
                        .then(creditService.deductForConversation(userId, session2))
                        .then(creditService.deductForConversation(userId, session3))
                        .then(creditService.getBalance(userId)),
                ).assertNext { credit -> assertThat(credit.balance()).isEqualTo(4700L) }
                .verifyComplete()

            StepVerifier
                .create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
                .expectNextCount(4)
                .verifyComplete()
        }

        @Test
        @DisplayName("각 차감 트랜잭션의 balanceBefore/After가 연속적으로 이어진다")
        fun consecutiveDeductions_balanceChainIsConsistent() {
            val userId = UserId.of("deduct-chain-user")
            val session1 = ConversationSessionId.of("chain-sess-1")
            val session2 = ConversationSessionId.of("chain-sess-2")

            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userId)
                        .then(creditService.deductForConversation(userId, session1))
                        .then(creditService.deductForConversation(userId, session2))
                        .thenMany(creditService.getTransactions(userId, PageRequest.of(0, 10))),
                ).assertNext { tx ->
                    assertThat(tx.type()).isEqualTo(CreditTransactionType.DEDUCT)
                    assertThat(tx.balanceBefore()).isEqualTo(4900L)
                    assertThat(tx.balanceAfter()).isEqualTo(4800L)
                }.assertNext { tx ->
                    assertThat(tx.type()).isEqualTo(CreditTransactionType.DEDUCT)
                    assertThat(tx.balanceBefore()).isEqualTo(5000L)
                    assertThat(tx.balanceAfter()).isEqualTo(4900L)
                }.assertNext { tx -> assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE) }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("잔액 부족 시 차감 거부")
    inner class InsufficientCreditFlow {
        @Test
        @DisplayName("잔액 50에서 100 차감 시도 시 예외 발생, 잔액과 트랜잭션 수가 변하지 않는다")
        fun deduct_insufficient_balanceAndTxCountUnchanged() {
            val userId = UserId.of("insufficient-user-1")
            val lowCredit = UserCredit(userId, 50L, 0L)

            StepVerifier
                .create(
                    userCreditRepo
                        .save(UserCreditDocument.fromDomain(lowCredit))
                        .then(creditService.deductForConversation(userId, ConversationSessionId.of("s-fail"))),
                ).expectError(InsufficientCreditException::class.java)
                .verify()

            StepVerifier
                .create(creditService.getBalance(userId))
                .assertNext { credit -> assertThat(credit.balance()).isEqualTo(50L) }
                .verifyComplete()

            StepVerifier
                .create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
                .verifyComplete()
        }

        @Test
        @DisplayName("잔액이 정확히 99일 때 100 차감 시도 시 거부된다")
        fun deduct_balanceIs99_rejected() {
            val userId = UserId.of("boundary-user")
            val nearThreshold = UserCredit(userId, 99L, 0L)

            StepVerifier
                .create(
                    userCreditRepo
                        .save(UserCreditDocument.fromDomain(nearThreshold))
                        .then(creditService.deductForConversation(userId, ConversationSessionId.of("s-boundary"))),
                ).expectError(InsufficientCreditException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("결제 충전")
    inner class PaymentChargeFlow {
        @Test
        @DisplayName("결제 충전 후 잔액이 증가하고 PAYMENT_CHARGE 트랜잭션이 기록된다")
        fun chargeByPayment_increasesBalanceAndRecordsTx() {
            val userId = UserId.of("payment-user-1")
            val source = PaymentCharge("toss-pay-abc", "toss")

            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userId)
                        .then(creditService.chargeByPayment(userId, 10000L, source))
                        .then(creditService.getBalance(userId)),
                ).assertNext { credit -> assertThat(credit.balance()).isEqualTo(15000L) }
                .verifyComplete()

            StepVerifier
                .create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
                .assertNext { tx ->
                    assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE)
                    assertThat(tx.source().sourceType()).isEqualTo(CreditSourceType.PAYMENT_CHARGE)
                    assertThat(tx.amount()).isEqualTo(10000L)
                    assertThat(tx.referenceId()).isEqualTo("toss-pay-abc")
                }.assertNext { tx ->
                    assertThat(tx.source().sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS)
                }.verifyComplete()
        }
    }

    @Nested
    @DisplayName("initializeIfAbsent 멱등성")
    inner class InitializeIfAbsentIdempotency {
        @Test
        @DisplayName("initializeIfAbsent 두 번 호출해도 가입 보너스가 한 번만 지급된다")
        fun initializeIfAbsent_calledTwice_bonusGrantedOnce() {
            val userId = UserId.of("idempotent-user-1")

            StepVerifier
                .create(
                    creditService
                        .initializeIfAbsent(userId)
                        .then(creditService.initializeIfAbsent(userId))
                        .then(creditService.getBalance(userId)),
                ).assertNext { credit -> assertThat(credit.balance()).isEqualTo(5000L) }
                .verifyComplete()

            StepVerifier
                .create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
                .expectNextCount(1)
                .verifyComplete()
        }

        @Test
        @DisplayName("이미 크레딧이 있는 유저에게 initializeIfAbsent 호출해도 잔액이 변하지 않는다")
        fun initializeIfAbsent_existingUser_noChange() {
            val userId = UserId.of("idempotent-existing-user")

            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userId)
                        .then(creditService.deductForConversation(userId, ConversationSessionId.of("s-x")))
                        .then(creditService.initializeIfAbsent(userId))
                        .then(creditService.getBalance(userId)),
                ).assertNext { credit -> assertThat(credit.balance()).isEqualTo(4900L) }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("유저 간 격리")
    inner class UserIsolation {
        @Test
        @DisplayName("유저 A의 차감이 유저 B의 잔액에 영향을 주지 않는다")
        fun deductUserA_doesNotAffectUserB() {
            val userA = UserId.of("isolation-user-a")
            val userB = UserId.of("isolation-user-b")

            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userA)
                        .then(creditService.grantSignupBonus(userB))
                        .then(creditService.deductForConversation(userA, ConversationSessionId.of("s-a-1")))
                        .then(creditService.deductForConversation(userA, ConversationSessionId.of("s-a-2")))
                        .then(creditService.getBalance(userB)),
                ).assertNext { credit -> assertThat(credit.balance()).isEqualTo(5000L) }
                .verifyComplete()
        }

        @Test
        @DisplayName("두 유저의 트랜잭션이 서로 섞이지 않는다")
        fun transactions_areIsolatedPerUser() {
            val userA = UserId.of("tx-isolation-a")
            val userB = UserId.of("tx-isolation-b")

            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userA)
                        .then(creditService.grantSignupBonus(userB))
                        .then(creditService.deductForConversation(userA, ConversationSessionId.of("s-a")))
                        .thenMany(creditService.getTransactions(userA, PageRequest.of(0, 10))),
                ).assertNext { tx -> assertThat(tx.userId()).isEqualTo(userA) }
                .assertNext { tx -> assertThat(tx.userId()).isEqualTo(userA) }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("동시 차감 시나리오")
    inner class ConcurrentDeduction {
        @Test
        @DisplayName("동시에 50번 차감 요청 시 성공한 요청만큼만 잔액이 감소한다 (잔액은 0 이상)")
        fun concurrent_deductions_balanceNeverGoesNegative() {
            val userId = UserId.of("concurrent-user-1")
            creditService.grantSignupBonus(userId).block()

            val deductions =
                Flux
                    .range(0, 50)
                    .flatMap { index ->
                        creditService
                            .deductForConversation(userId, ConversationSessionId.of("concurrent-s-$index"))
                            .thenReturn(1L)
                            .onErrorReturn(0L)
                    }

            val succeeded = deductions.collectList().block()!!.sum()

            StepVerifier
                .create(creditService.getBalance(userId))
                .assertNext { credit -> assertThat(credit.balance()).isGreaterThanOrEqualTo(0L) }
                .verifyComplete()

            val finalBalance = creditService.getBalance(userId).block()!!.balance()
            assertThat(5000L - finalBalance).isEqualTo(succeeded * 100L)
        }
    }
}
