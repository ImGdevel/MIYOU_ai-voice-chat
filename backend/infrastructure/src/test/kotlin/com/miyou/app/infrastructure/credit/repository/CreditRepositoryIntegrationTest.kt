package com.miyou.app.infrastructure.credit.repository

import com.miyou.app.config.annotation.ReactiveRepositoryTest
import com.miyou.app.domain.credit.model.ConversationDeduction
import com.miyou.app.domain.credit.model.CreditSourceType
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.SignupBonus
import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.infrastructure.credit.adapter.CreditTransactionMongoAdapter
import com.miyou.app.infrastructure.credit.adapter.UserCreditMongoAdapter
import com.miyou.app.infrastructure.credit.document.CreditTransactionDocument
import com.miyou.app.support.ContainerizedIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import reactor.test.StepVerifier

@ReactiveRepositoryTest
@Import(UserCreditMongoAdapter::class, CreditTransactionMongoAdapter::class)
@DisplayName("[통합] Credit MongoDB Repository")
class CreditRepositoryIntegrationTest : ContainerizedIntegrationTestSupport() {
    @Autowired
    private lateinit var userCreditRepo: UserCreditMongoRepository

    @Autowired
    private lateinit var creditTxRepo: CreditTransactionMongoRepository

    @Autowired
    private lateinit var userCreditAdapter: UserCreditMongoAdapter

    @Autowired
    private lateinit var creditTxAdapter: CreditTransactionMongoAdapter

    @BeforeEach
    @AfterEach
    fun cleanUp() {
        userCreditRepo.deleteAll().block()
        creditTxRepo.deleteAll().block()
    }

    @Test
    @DisplayName("신규 UserCredit 저장 후 userId로 조회할 수 있다")
    fun save_thenFindByUserId_returnsCorrectBalance() {
        // given: 통합 테스트 대상 사용자 아이디 및 데이터 초기화
        val userId = "integration-user-1"
        val credit = UserCredit.initialize(userId, 5000L)

        // when & then: 사용자 크레딧을 저장한 후에 ID로 조회하여 정확한 잔액을 리턴하는지 검증
        StepVerifier
            .create(userCreditAdapter.save(credit).then(userCreditAdapter.findByUserId(userId)))
            .assertNext { found -> assertThat(found.balance).isEqualTo(5000L) }
            .verifyComplete()
    }

    @Test
    @DisplayName("트랜잭션 저장 후 최신순으로 조회한다")
    fun saveMultiple_thenFindByUserId_returnsDescOrder() {
        // given: 하나의 사용자에 대해 순차적으로 세 건의 거래 생성 (시간 간격 부여)
        val userId = "tx-order-user"
        val tx1 = CreditTransaction.of(userId, CreditTransactionType.CHARGE, SignupBonus, 5000L, 0L, 5000L)
        Thread.sleep(5)
        val tx2 =
            CreditTransaction.of(
                userId,
                CreditTransactionType.DEDUCT,
                ConversationDeduction(ConversationSessionFixture.createId().value),
                100L,
                5000L,
                4900L,
            )
        Thread.sleep(5)
        val tx3 =
            CreditTransaction.of(
                userId,
                CreditTransactionType.DEDUCT,
                ConversationDeduction(ConversationSessionFixture.createId().value),
                100L,
                4900L,
                4800L,
            )

        // when & then: 세 건의 거래를 저장 후 최신순 정렬 조회를 요청하여 순서대로 데이터 확인
        StepVerifier
            .create(
                creditTxAdapter
                    .save(tx1)
                    .then(creditTxAdapter.save(tx2))
                    .then(creditTxAdapter.save(tx3))
                    .thenMany(creditTxAdapter.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10))),
            ).assertNext { tx ->
                // 최신 거래 (tx3, 차감 전 4900L)
                assertThat(tx.balanceBefore).isEqualTo(4900L)
            }.assertNext { tx ->
                // 중간 거래 (tx2, 차감 전 5000L)
                assertThat(tx.balanceBefore).isEqualTo(5000L)
            }.assertNext { tx ->
                // 최초 거래 (tx1, CHARGE 타입)
                assertThat(tx.type).isEqualTo(CreditTransactionType.CHARGE)
            }.verifyComplete()
    }

    @Test
    @DisplayName("ConversationDeduction은 sessionId를 보존한다")
    fun conversationDeduction_persistsSessionId() {
        // given: 대화 세션 ID 기반 차감 거래 생성
        val userId = "source-deduction-user"
        val sessionId = ConversationSessionFixture.createId("my-session-xyz")
        val tx =
            CreditTransaction.of(
                userId,
                CreditTransactionType.DEDUCT,
                ConversationDeduction(sessionId.value),
                100L,
                5000L,
                4900L,
                "my-session-xyz",
            )

        // when & then: MongoDB에 직접 저장 후 저장된 원본 다큐먼트에서 복원 시 session-id가 유지되는지 확인
        StepVerifier
            .create(
                creditTxAdapter
                    .save(tx)
                    .then(creditTxRepo.findById(tx.transactionId.value))
                    .map(CreditTransactionDocument::toDomain),
            ).assertNext { restored ->
                assertThat(restored.source.sourceType()).isEqualTo(CreditSourceType.CONVERSATION_DEDUCTION)
                assertThat((restored.source as ConversationDeduction).sessionId()).isEqualTo("my-session-xyz")
            }.verifyComplete()
    }

    @Test
    @DisplayName("PaymentCharge는 payment 정보와 provider를 보존한다")
    fun paymentCharge_persistsPaymentInfo() {
        // given: 결제 충전 거래 생성
        val userId = "source-payment-user"
        val tx =
            CreditTransaction.of(
                userId,
                CreditTransactionType.CHARGE,
                PaymentCharge("toss-pay-001", "toss"),
                10000L,
                0L,
                10000L,
                "toss-pay-001",
            )

        // when & then: 저장 후 리포지토리에서 꺼내 복원 시 paymentId 등 정보가 보존되는지 확인
        StepVerifier
            .create(
                creditTxAdapter
                    .save(tx)
                    .then(creditTxRepo.findById(tx.transactionId.value))
                    .map(CreditTransactionDocument::toDomain),
            ).assertNext { restored ->
                assertThat(restored.source.sourceType()).isEqualTo(CreditSourceType.PAYMENT_CHARGE)
                assertThat((restored.source as PaymentCharge).paymentId()).isEqualTo("toss-pay-001")
            }.verifyComplete()
    }
}
