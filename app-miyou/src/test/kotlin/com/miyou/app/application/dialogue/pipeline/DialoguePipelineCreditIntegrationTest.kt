package com.miyou.app.application.dialogue.pipeline

import com.miyou.app.application.credit.service.CreditApplicationService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueInputService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueLlmStreamService
import com.miyou.app.application.dialogue.pipeline.stage.DialoguePostProcessingService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueTtsStreamService
import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.infrastructure.credit.adapter.CreditTransactionMongoAdapter
import com.miyou.app.infrastructure.credit.adapter.UserCreditMongoAdapter
import com.miyou.app.infrastructure.credit.repository.CreditTransactionMongoRepository
import com.miyou.app.infrastructure.credit.repository.UserCreditMongoRepository
import com.miyou.app.support.ContainerizedIntegrationTestSupport
import com.miyou.app.support.anyValue
import com.miyou.app.support.eqValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

/**
 * 대화 파이프라인의 선차감/환불 경계 로직을 실제 MongoDB 잔액으로 검증한다.
 *
 * [DialoguePipelineServiceTest]가 같은 시나리오를 mock `CreditDeductUseCase`에 대한
 * `verify()` 호출로 검증하는 것과 달리, 여기서는 실제 `CreditApplicationService` +
 * Testcontainers MongoDB를 사용해 DB에 저장된 잔액/트랜잭션 개수를 직접 확인한다.
 * (docs/troubleshooting/credit-precharge-refund-boundary.md 참고)
 */
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
@DisplayName("[통합] 대화 파이프라인 선차감/환불 경계 - 실제 크레딧 잔액 검증")
class DialoguePipelineCreditIntegrationTest : ContainerizedIntegrationTestSupport() {
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

    private fun sessionFor(
        userIdValue: String,
        sessionIdValue: String,
    ): ConversationSession =
        ConversationSession(
            ConversationSessionId.of(sessionIdValue),
            PersonaId.defaultPersona(),
            UserId.of(userIdValue),
            Instant.now(),
            null,
        )

    private fun emptyInputsFor(
        session: ConversationSession,
        text: String,
    ): PipelineInputs {
        val currentTurn = ConversationTurn.create(session.sessionId, text)
        return PipelineInputs(
            session,
            RetrievalContext.empty(text),
            MemoryRetrievalResult.empty(),
            ConversationContext.empty(),
            currentTurn,
        )
    }

    private fun buildPipeline(
        inputService: DialogueInputService,
        llmStreamService: DialogueLlmStreamService,
        ttsStreamService: DialogueTtsStreamService,
        postProcessingService: DialoguePostProcessingService,
    ): DialoguePipelineService =
        DialoguePipelineService(
            inputService,
            llmStreamService,
            ttsStreamService,
            postProcessingService,
            creditService,
        )

    @Nested
    @DisplayName("정상 완료")
    inner class SuccessFlow {
        @Test
        @DisplayName("텍스트 대화 정상 완료 시 실제 DB 잔액이 차감된 채로 유지된다")
        fun executeTextOnly_success_deductsRealBalance() {
            val session = sessionFor("pipeline-success-user", "pipeline-success-session")
            val text = "hello"
            val inputs = emptyInputsFor(session, text)

            val inputService = mock(DialogueInputService::class.java)
            val llmStreamService = mock(DialogueLlmStreamService::class.java)
            val ttsStreamService = mock(DialogueTtsStreamService::class.java)
            val postProcessingService = mock(DialoguePostProcessingService::class.java)

            `when`(inputService.prepareInputs(eqValue(session), eqValue(text))).thenReturn(Mono.just(inputs))
            `when`(llmStreamService.buildLlmTokenStream(anyValue())).thenReturn(Flux.just("hi"))
            `when`(postProcessingService.persistAndExtractText(anyValue(), anyValue())).thenReturn(Mono.empty())

            val pipeline = buildPipeline(inputService, llmStreamService, ttsStreamService, postProcessingService)
            creditService.grantSignupBonus(session.userId).block()

            StepVerifier
                .create(pipeline.executeTextOnly(session, text))
                .expectNext("hi")
                .verifyComplete()

            StepVerifier
                .create(creditService.getBalance(session.userId))
                .assertNext { credit -> assertThat(credit.balance()).isEqualTo(4900L) }
                .verifyComplete()

            StepVerifier
                .create(creditService.getTransactions(session.userId, PageRequest.of(0, 10)))
                .expectNextCount(2) // signup bonus + deduct
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("postProcessing 실패")
    inner class PostProcessingFailureFlow {
        @Test
        @DisplayName("대화 저장 실패해도 실제 DB 잔액은 환불되지 않고 차감된 채로 유지된다")
        fun executeTextOnly_postProcessingFails_realBalanceStaysDeducted() {
            val session = sessionFor("pipeline-postfail-user", "pipeline-postfail-session")
            val text = "hello"
            val inputs = emptyInputsFor(session, text)

            val inputService = mock(DialogueInputService::class.java)
            val llmStreamService = mock(DialogueLlmStreamService::class.java)
            val ttsStreamService = mock(DialogueTtsStreamService::class.java)
            val postProcessingService = mock(DialoguePostProcessingService::class.java)

            `when`(inputService.prepareInputs(eqValue(session), eqValue(text))).thenReturn(Mono.just(inputs))
            `when`(llmStreamService.buildLlmTokenStream(anyValue())).thenReturn(Flux.just("hi"))
            `when`(postProcessingService.persistAndExtractText(anyValue(), anyValue()))
                .thenReturn(Mono.error(RuntimeException("mongodb unavailable")))

            val pipeline = buildPipeline(inputService, llmStreamService, ttsStreamService, postProcessingService)
            creditService.grantSignupBonus(session.userId).block()

            StepVerifier
                .create(pipeline.executeTextOnly(session, text))
                .expectNext("hi")
                .expectError(RuntimeException::class.java)
                .verify()

            StepVerifier
                .create(creditService.getBalance(session.userId))
                .assertNext { credit -> assertThat(credit.balance()).isEqualTo(4900L) }
                .verifyComplete()

            StepVerifier
                .create(creditService.getTransactions(session.userId, PageRequest.of(0, 10)))
                .expectNextCount(2) // signup bonus + deduct only, no refund
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("서비스 실패")
    inner class ServiceFailureFlow {
        @Test
        @DisplayName("LLM 스트림 실패 시 실제 DB 잔액이 환불되어 원래대로 복구된다")
        fun executeTextOnly_serviceFails_realBalanceIsRefunded() {
            val session = sessionFor("pipeline-svcfail-user", "pipeline-svcfail-session")
            val text = "hello"
            val inputs = emptyInputsFor(session, text)
            val failure = IllegalStateException("llm failed")

            val inputService = mock(DialogueInputService::class.java)
            val llmStreamService = mock(DialogueLlmStreamService::class.java)
            val ttsStreamService = mock(DialogueTtsStreamService::class.java)
            val postProcessingService = mock(DialoguePostProcessingService::class.java)

            `when`(inputService.prepareInputs(eqValue(session), eqValue(text))).thenReturn(Mono.just(inputs))
            `when`(llmStreamService.buildLlmTokenStream(anyValue())).thenReturn(Flux.error(failure))
            `when`(postProcessingService.persistAndExtractText(anyValue(), anyValue())).thenReturn(Mono.empty())

            val pipeline = buildPipeline(inputService, llmStreamService, ttsStreamService, postProcessingService)
            creditService.grantSignupBonus(session.userId).block()

            StepVerifier
                .create(pipeline.executeTextOnly(session, text))
                .expectErrorSatisfies { error -> assertThat(error).isSameAs(failure) }
                .verify()

            StepVerifier
                .create(creditService.getBalance(session.userId))
                .assertNext { credit -> assertThat(credit.balance()).isEqualTo(5000L) }
                .verifyComplete()

            StepVerifier
                .create(creditService.getTransactions(session.userId, PageRequest.of(0, 10)))
                .expectNextCount(3) // signup bonus + deduct + refund
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("사용자 취소")
    inner class UserCancellationFlow {
        @Test
        @DisplayName("사용자가 스트림을 취소해도 실제 DB 잔액은 환불되지 않고 차감된 채로 유지된다")
        fun executeTextOnly_userCancels_realBalanceStaysDeducted() {
            val session = sessionFor("pipeline-cancel-user", "pipeline-cancel-session")
            val text = "hello"
            val inputs = emptyInputsFor(session, text)

            val inputService = mock(DialogueInputService::class.java)
            val llmStreamService = mock(DialogueLlmStreamService::class.java)
            val ttsStreamService = mock(DialogueTtsStreamService::class.java)
            val postProcessingService = mock(DialoguePostProcessingService::class.java)

            `when`(inputService.prepareInputs(eqValue(session), eqValue(text))).thenReturn(Mono.just(inputs))
            `when`(llmStreamService.buildLlmTokenStream(anyValue()))
                .thenReturn(Flux.just("tok1", "tok2", "tok3").concatWith(Flux.never()))
            `when`(postProcessingService.persistAndExtractText(anyValue(), anyValue())).thenReturn(Mono.empty())

            val pipeline = buildPipeline(inputService, llmStreamService, ttsStreamService, postProcessingService)
            creditService.grantSignupBonus(session.userId).block()

            StepVerifier
                .create(pipeline.executeTextOnly(session, text))
                .expectNext("tok1")
                .thenCancel()
                .verify()

            StepVerifier
                .create(creditService.getBalance(session.userId))
                .assertNext { credit -> assertThat(credit.balance()).isEqualTo(4900L) }
                .verifyComplete()

            StepVerifier
                .create(creditService.getTransactions(session.userId, PageRequest.of(0, 10)))
                .expectNextCount(2) // signup bonus + deduct only, no refund
                .verifyComplete()
        }
    }
}
