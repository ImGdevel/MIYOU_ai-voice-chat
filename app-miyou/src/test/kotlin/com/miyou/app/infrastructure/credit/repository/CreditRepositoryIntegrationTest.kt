package com.miyou.app.infrastructure.credit.repository

import com.miyou.app.config.annotation.ReactiveRepositoryTest
import com.miyou.app.domain.credit.model.ConversationDeduction
import com.miyou.app.domain.credit.model.CreditSourceType
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.SignupBonus
import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.domain.dialogue.model.UserId
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
		val userId = UserId.of("integration-user-1")
		val credit = UserCredit.initialize(userId, 5000L)

		StepVerifier.create(userCreditAdapter.save(credit).then(userCreditAdapter.findByUserId(userId)))
			.assertNext { found -> assertThat(found.balance()).isEqualTo(5000L) }
			.verifyComplete()
	}

	@Test
	@DisplayName("트랜잭션 저장 후 최신순으로 조회한다")
	fun saveMultiple_thenFindByUserId_returnsDescOrder() {
		val userId = UserId.of("tx-order-user")
		val tx1 = CreditTransaction.of(userId, CreditTransactionType.CHARGE, SignupBonus(), 5000L, 0L, 5000L)
		Thread.sleep(5)
		val tx2 = CreditTransaction.of(
			userId,
			CreditTransactionType.DEDUCT,
			ConversationDeduction(ConversationSessionFixture.createId()),
			100L,
			5000L,
			4900L,
		)
		Thread.sleep(5)
		val tx3 = CreditTransaction.of(
			userId,
			CreditTransactionType.DEDUCT,
			ConversationDeduction(ConversationSessionFixture.createId()),
			100L,
			4900L,
			4800L,
		)

		StepVerifier.create(
			creditTxAdapter.save(tx1)
				.then(creditTxAdapter.save(tx2))
				.then(creditTxAdapter.save(tx3))
				.thenMany(creditTxAdapter.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10))),
		)
			.assertNext { tx -> assertThat(tx.balanceBefore()).isEqualTo(4900L) }
			.assertNext { tx -> assertThat(tx.balanceBefore()).isEqualTo(5000L) }
			.assertNext { tx -> assertThat(tx.type()).isEqualTo(CreditTransactionType.CHARGE) }
			.verifyComplete()
	}

	@Test
	@DisplayName("ConversationDeduction은 sessionId를 보존한다")
	fun conversationDeduction_persistsSessionId() {
		val userId = UserId.of("source-deduction-user")
		val sessionId = ConversationSessionFixture.createId("my-session-xyz")
		val tx = CreditTransaction.of(
			userId,
			CreditTransactionType.DEDUCT,
			ConversationDeduction(sessionId),
			100L,
			5000L,
			4900L,
			"my-session-xyz",
		)

		StepVerifier.create(
			creditTxAdapter.save(tx)
				.then(creditTxRepo.findById(tx.transactionId().value()))
				.map(CreditTransactionDocument::toDomain),
		)
			.assertNext { restored ->
				assertThat(restored.source().sourceType()).isEqualTo(CreditSourceType.CONVERSATION_DEDUCTION)
				assertThat((restored.source() as ConversationDeduction).sessionId().value()).isEqualTo("my-session-xyz")
			}
			.verifyComplete()
	}

	@Test
	@DisplayName("PaymentCharge는 payment 정보와 provider를 보존한다")
	fun paymentCharge_persistsPaymentInfo() {
		val userId = UserId.of("source-payment-user")
		val tx = CreditTransaction.of(
			userId,
			CreditTransactionType.CHARGE,
			PaymentCharge("toss-pay-001", "toss"),
			10000L,
			0L,
			10000L,
			"toss-pay-001",
		)

		StepVerifier.create(
			creditTxAdapter.save(tx)
				.then(creditTxRepo.findById(tx.transactionId().value()))
				.map(CreditTransactionDocument::toDomain),
		)
			.assertNext { restored ->
				assertThat(restored.source().sourceType()).isEqualTo(CreditSourceType.PAYMENT_CHARGE)
				assertThat((restored.source() as PaymentCharge).paymentId()).isEqualTo("toss-pay-001")
			}
			.verifyComplete()
	}
}
