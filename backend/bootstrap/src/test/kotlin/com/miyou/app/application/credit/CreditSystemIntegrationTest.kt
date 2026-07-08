package com.miyou.app.application.credit

import com.miyou.app.application.credit.service.CreditApplicationService
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.credit.model.CreditSourceType
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.UserCredit
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
    @DisplayName("가입 보너스 지급 테스트")
    inner class SignupBonusFlow {
        @Test
        @DisplayName("신규 유저에게 5000 크레딧 가입 보너스가 지급되고 트랜잭션이 기록된다")
        fun grantSignupBonus_newUser_5000CreditsAndTransactionRecorded() {
            // given
            val userId = "signup-user-1"

            // when & then: 가입 보너스 지급 시 생성된 거래 확인
            StepVerifier
                .create(creditService.grantSignupBonus(userId))
                .assertNext { tx ->
                    assertThat(tx.type).isEqualTo(CreditTransactionType.CHARGE)
                    assertThat(tx.amount).isEqualTo(5000L)
                    assertThat(tx.balanceBefore).isEqualTo(0L)
                    assertThat(tx.balanceAfter).isEqualTo(5000L)
                    assertThat(tx.source.sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS)
                }.verifyComplete()

            // 잔액 검증
            StepVerifier
                .create(creditService.getBalance(userId))
                .assertNext { credit -> assertThat(credit.balance).isEqualTo(5000L) }
                .verifyComplete()

            // 거래 기록 개수 검증
            StepVerifier
                .create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
                .expectNextCount(1)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("연속 대화 차감 테스트")
    inner class ConsecutiveDeductionFlow {
        @Test
        @DisplayName("3번 연속 차감 후 잔액이 4700이 되고 트랜잭션 4건(보너스+3차감)이 기록된다")
        fun consecutiveDeductions_balanceDecreasesCorrectly() {
            // given
            val userId = "deduct-user-1"
            val session1 = "sess-1"
            val session2 = "sess-2"
            val session3 = "sess-3"

            // when & then: 가입 보너스 부여 후 3회 연속 대화 비용 차감
            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userId)
                        .then(creditService.deductForConversation(userId, session1))
                        .then(creditService.deductForConversation(userId, session2))
                        .then(creditService.deductForConversation(userId, session3))
                        .then(creditService.getBalance(userId)),
                ).assertNext { credit -> assertThat(credit.balance).isEqualTo(4700L) }
                .verifyComplete()

            // 총 4건의 거래 내역이 저장되었는지 확인
            StepVerifier
                .create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
                .expectNextCount(4)
                .verifyComplete()
        }

        @Test
        @DisplayName("각 차감 트랜잭션의 balanceBefore/After가 연속적으로 이어진다")
        fun consecutiveDeductions_balanceChainIsConsistent() {
            // given
            val userId = "deduct-chain-user"
            val session1 = "chain-sess-1"
            val session2 = "chain-sess-2"

            // when & then: 차감 체인 발생 후 거래 내역을 역순(최신순)으로 반환받아 잔액 전후 관계 검증
            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userId)
                        .then(creditService.deductForConversation(userId, session1))
                        .then(creditService.deductForConversation(userId, session2))
                        .thenMany(creditService.getTransactions(userId, PageRequest.of(0, 10))),
                ).assertNext { tx ->
                    // 두 번째 차감 거래 (4900 -> 4800)
                    assertThat(tx.type).isEqualTo(CreditTransactionType.DEDUCT)
                    assertThat(tx.balanceBefore).isEqualTo(4900L)
                    assertThat(tx.balanceAfter).isEqualTo(4800L)
                }.assertNext { tx ->
                    // 첫 번째 차감 거래 (5000 -> 4900)
                    assertThat(tx.type).isEqualTo(CreditTransactionType.DEDUCT)
                    assertThat(tx.balanceBefore).isEqualTo(5000L)
                    assertThat(tx.balanceAfter).isEqualTo(4900L)
                }.assertNext { tx ->
                    // 최초 가입 보너스 충전 (0 -> 5000)
                    assertThat(tx.type).isEqualTo(CreditTransactionType.CHARGE)
                }.verifyComplete()
        }
    }

    @Nested
    @DisplayName("잔액 부족 시 차감 거부 테스트")
    inner class InsufficientCreditFlow {
        @Test
        @DisplayName("잔액 50에서 100 차감 시도 시 예외 발생, 잔액과 트랜잭션 수가 변하지 않는다")
        fun deduct_insufficient_balanceAndTxCountUnchanged() {
            // given: 50 크레딧을 가진 사용자 생성
            val userId = "insufficient-user-1"
            val lowCredit = UserCredit(userId, 50L, 0L)

            // when & then: 100 크레딧 차감 시 InsufficientCreditException 예외 발생 검증
            StepVerifier
                .create(
                    userCreditRepo
                        .save(UserCreditDocument.fromDomain(lowCredit))
                        .then(creditService.deductForConversation(userId, "s-fail")),
                ).expectError(InsufficientCreditException::class.java)
                .verify()

            // 차감 거부 후에도 잔액 변화 없음 확인
            StepVerifier
                .create(creditService.getBalance(userId))
                .assertNext { credit -> assertThat(credit.balance).isEqualTo(50L) }
                .verifyComplete()

            // 차감 실패 건은 거래 내역에 기록되지 않아야 함
            StepVerifier
                .create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
                .verifyComplete()
        }

        @Test
        @DisplayName("잔액이 정확히 99일 때 100 차감 시도 시 거부된다")
        fun deduct_balanceIs99_rejected() {
            // given: 임계값 바로 아래(99 크레딧) 상태
            val userId = "boundary-user"
            val nearThreshold = UserCredit(userId, 99L, 0L)

            // when & then: 100 크레딧 차감 요청 시 실패 확인
            StepVerifier
                .create(
                    userCreditRepo
                        .save(UserCreditDocument.fromDomain(nearThreshold))
                        .then(creditService.deductForConversation(userId, "s-boundary")),
                ).expectError(InsufficientCreditException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("결제 충전 테스트")
    inner class PaymentChargeFlow {
        @Test
        @DisplayName("결제 충전 후 잔액이 증가하고 PAYMENT_CHARGE 트랜잭션이 기록된다")
        fun chargeByPayment_increasesBalanceAndRecordsTx() {
            // given: 결제 충전 정보 설정
            val userId = "payment-user-1"
            val source = PaymentCharge("toss-pay-abc", "toss")

            // when & then: 가입 보너스 후 결제 충전(10,000L) 진행하여 잔액 합산 검증
            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userId)
                        .then(creditService.chargeByPayment(userId, 10000L, source))
                        .then(creditService.getBalance(userId)),
                ).assertNext { credit -> assertThat(credit.balance).isEqualTo(15000L) }
                .verifyComplete()

            // 충전 관련 세부 거래 기록 확인
            StepVerifier
                .create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
                .assertNext { tx ->
                    assertThat(tx.type).isEqualTo(CreditTransactionType.CHARGE)
                    assertThat(tx.source.sourceType()).isEqualTo(CreditSourceType.PAYMENT_CHARGE)
                    assertThat(tx.amount).isEqualTo(10000L)
                    assertThat(tx.referenceId).isEqualTo("toss-pay-abc")
                }.assertNext { tx ->
                    assertThat(tx.source.sourceType()).isEqualTo(CreditSourceType.SIGNUP_BONUS)
                }.verifyComplete()
        }
    }

    @Nested
    @DisplayName("initializeIfAbsent 멱등성 테스트")
    inner class InitializeIfAbsentIdempotency {
        @Test
        @DisplayName("initializeIfAbsent 두 번 호출해도 가입 보너스가 한 번만 지급된다")
        fun initializeIfAbsent_calledTwice_bonusGrantedOnce() {
            // given
            val userId = "idempotent-user-1"

            // when & then: 두 번의 초기화 동작 요청 시 최종 5000 크레딧만 설정되는지 확인
            StepVerifier
                .create(
                    creditService
                        .initializeIfAbsent(userId)
                        .then(creditService.initializeIfAbsent(userId))
                        .then(creditService.getBalance(userId)),
                ).assertNext { credit -> assertThat(credit.balance).isEqualTo(5000L) }
                .verifyComplete()

            // 거래 기록도 1건만 존재해야 함
            StepVerifier
                .create(creditService.getTransactions(userId, PageRequest.of(0, 10)))
                .expectNextCount(1)
                .verifyComplete()
        }

        @Test
        @DisplayName("이미 크레딧이 있는 유저에게 initializeIfAbsent 호출해도 잔액이 변하지 않는다")
        fun initializeIfAbsent_existingUser_noChange() {
            // given
            val userId = "idempotent-existing-user"

            // when & then: 가입 보너스 발급 및 대화 1회 차감 후 다시 초기화 시 잔액 보존 검증
            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userId)
                        .then(creditService.deductForConversation(userId, "s-x"))
                        .then(creditService.initializeIfAbsent(userId))
                        .then(creditService.getBalance(userId)),
                ).assertNext { credit -> assertThat(credit.balance).isEqualTo(4900L) }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("유저 간 격리 테스트")
    inner class UserIsolation {
        @Test
        @DisplayName("유저 A의 차감이 유저 B의 잔액에 영향을 주지 않는다")
        fun deductUserA_doesNotAffectUserB() {
            // given
            val userA = "isolation-user-a"
            val userB = "isolation-user-b"

            // when & then: 유저 A 위주로 차감 동작을 돌린 후 유저 B의 잔액이 그대로(5000L)인지 확인
            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userA)
                        .then(creditService.grantSignupBonus(userB))
                        .then(creditService.deductForConversation(userA, "s-a-1"))
                        .then(creditService.deductForConversation(userA, "s-a-2"))
                        .then(creditService.getBalance(userB)),
                ).assertNext { credit -> assertThat(credit.balance).isEqualTo(5000L) }
                .verifyComplete()
        }

        @Test
        @DisplayName("두 유저의 트랜잭션이 서로 섞이지 않는다")
        fun transactions_areIsolatedPerUser() {
            // given
            val userA = "tx-isolation-a"
            val userB = "tx-isolation-b"

            // when & then: 서로 다른 두 유저 가입 보너스 후 유저 A만 대화 차감 수행 시 A의 거래만 조회되는지 확인
            StepVerifier
                .create(
                    creditService
                        .grantSignupBonus(userA)
                        .then(creditService.grantSignupBonus(userB))
                        .then(creditService.deductForConversation(userA, "s-a"))
                        .thenMany(creditService.getTransactions(userA, PageRequest.of(0, 10))),
                ).assertNext { tx -> assertThat(tx.userId).isEqualTo(userA) }
                .assertNext { tx -> assertThat(tx.userId).isEqualTo(userA) }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("동시 차감 시나리오 테스트")
    inner class ConcurrentDeduction {
        @Test
        @DisplayName("동시에 50번 차감 요청 시 성공한 요청만큼만 잔액이 감소한다 (잔액은 0 이상)")
        fun concurrent_deductions_balanceNeverGoesNegative() {
            // given: 가입 보너스(5000 크레딧) 부여
            val userId = "concurrent-user-1"
            creditService.grantSignupBonus(userId).block()

            // when: 50개의 동시 차감 비동기 플로우 모의 실행
            val deductions =
                Flux
                    .range(0, 50)
                    .flatMap { index ->
                        creditService
                            .deductForConversation(userId, "concurrent-s-$index")
                            .thenReturn(1L)
                            .onErrorReturn(0L)
                    }

            val succeeded = deductions.collectList().block()!!.sum()

            // then: 최종 잔액이 0 이상인지 검증
            StepVerifier
                .create(creditService.getBalance(userId))
                .assertNext { credit -> assertThat(credit.balance).isGreaterThanOrEqualTo(0L) }
                .verifyComplete()

            // 성공한 차감 수량(1건당 100 크레딧)이 최종 감소량과 정확히 맞는지 대조
            val finalBalance = creditService.getBalance(userId).block()!!.balance
            assertThat(5000L - finalBalance).isEqualTo(succeeded * 100L)
        }
    }
}
