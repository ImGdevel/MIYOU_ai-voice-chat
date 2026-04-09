package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.credit.usecase.CreditDeductUseCase
import com.miyou.app.application.dialogue.pipeline.PipelineInputs
import com.miyou.app.application.memory.policy.MemoryExtractionPolicy
import com.miyou.app.application.memory.service.MemoryExtractionService
import com.miyou.app.application.monitoring.port.ConversationMetricsPort
import com.miyou.app.application.monitoring.service.PipelineTracer
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.domain.dialogue.port.LlmPort
import com.miyou.app.domain.memory.port.ConversationCounterPort
import com.miyou.app.fixture.ConversationSessionFixture
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
class DialoguePostProcessingServiceTest {
    @Mock
    private lateinit var conversationRepository: ConversationRepository

    @Mock
    private lateinit var conversationCounterPort: ConversationCounterPort

    @Mock
    private lateinit var memoryExtractionService: MemoryExtractionService

    @Mock
    private lateinit var llmPort: LlmPort

    @Mock
    private lateinit var pipelineTracer: PipelineTracer

    @Mock
    private lateinit var conversationMetricsConfiguration: ConversationMetricsPort

    @Mock
    private lateinit var creditDeductUseCase: CreditDeductUseCase

    private lateinit var service: DialoguePostProcessingService

    @BeforeEach
    fun setUp() {
        service =
            DialoguePostProcessingService(
                conversationRepository,
                conversationCounterPort,
                memoryExtractionService,
                llmPort,
                pipelineTracer,
                conversationMetricsConfiguration,
                creditDeductUseCase,
                MemoryExtractionPolicy(5),
            )
        lenient()
            .`when`(creditDeductUseCase.deductForConversation(any(), any()))
            .thenReturn(Mono.just(mock(CreditTransaction::class.java)))
    }

    @Test
    @DisplayName("대화 저장 시 응답을 포함하여 저장한다")
    fun persistAndExtract_shouldSaveConversationWithResponse() {
        val session = ConversationSessionFixture.create()
        val sessionId = session.sessionId()
        val turn = ConversationTurn.create(sessionId, "질문")
        val inputs = PipelineInputs(session, null, null, null, turn)

        `when`(pipelineTracer.tracePersistence<ConversationTurn>(any()))
            .thenAnswer { Mono.just(turn.withResponse("응답 문장1 응답 문장2")) }
        `when`(conversationCounterPort.increment(sessionId)).thenReturn(Mono.just(1L))

        StepVerifier
            .create(
                service.persistAndExtract(Mono.just(inputs), Flux.just("응답 문장1", "응답 문장2")),
            ).verifyComplete()

        verify(pipelineTracer).tracePersistence<ConversationTurn>(any())
        verify(conversationCounterPort).increment(sessionId)
    }

    @Test
    @DisplayName("대화 저장 후 카운터를 증가시킨다")
    fun persistAndExtract_shouldIncrementConversationCounter() {
        val session = ConversationSessionFixture.create()
        val sessionId = session.sessionId()
        val turn = ConversationTurn.create(sessionId, "질문")
        val inputs = PipelineInputs(session, null, null, null, turn)

        `when`(pipelineTracer.tracePersistence<ConversationTurn>(any()))
            .thenAnswer { Mono.just(turn.withResponse("응답")) }
        `when`(conversationCounterPort.increment(sessionId)).thenReturn(Mono.just(3L))

        StepVerifier
            .create(service.persistAndExtract(Mono.just(inputs), Flux.just("응답")))
            .verifyComplete()

        verify(conversationCounterPort).increment(sessionId)
    }

    @Test
    @DisplayName("임계값 도달 시 메모리 추출을 실행한다")
    fun persistAndExtract_shouldTriggerMemoryExtractionAtThreshold() {
        val session = ConversationSessionFixture.create()
        val sessionId = session.sessionId()
        val turn = ConversationTurn.create(sessionId, "질문")
        val inputs = PipelineInputs(session, null, null, null, turn)

        `when`(pipelineTracer.tracePersistence<ConversationTurn>(any()))
            .thenAnswer { Mono.just(turn.withResponse("응답")) }
        `when`(conversationCounterPort.increment(sessionId)).thenReturn(Mono.just(5L))
        `when`(memoryExtractionService.checkAndExtract(sessionId)).thenReturn(Mono.empty())

        StepVerifier
            .create(service.persistAndExtract(Mono.just(inputs), Flux.just("응답")))
            .verifyComplete()

        verify(memoryExtractionService).checkAndExtract(sessionId)
    }

    @Test
    @DisplayName("임계값 미만이면 메모리 추출을 건너뛴다")
    fun persistAndExtract_shouldNotTriggerMemoryExtractionBelowThreshold() {
        val session = ConversationSessionFixture.create()
        val sessionId = session.sessionId()
        val turn = ConversationTurn.create(sessionId, "질문")
        val inputs = PipelineInputs(session, null, null, null, turn)

        `when`(pipelineTracer.tracePersistence<ConversationTurn>(any()))
            .thenAnswer { Mono.just(turn.withResponse("응답")) }
        `when`(conversationCounterPort.increment(sessionId)).thenReturn(Mono.just(3L))

        StepVerifier
            .create(service.persistAndExtract(Mono.just(inputs), Flux.just("응답")))
            .verifyComplete()

        verify(memoryExtractionService, never()).checkAndExtract(any())
    }

    @Test
    @DisplayName("conversationThreshold가 0 이하면 예외를 발생시킨다")
    fun constructor_withInvalidThreshold_shouldThrowException() {
        assertThatThrownBy {
            DialoguePostProcessingService(
                conversationRepository,
                conversationCounterPort,
                memoryExtractionService,
                llmPort,
                pipelineTracer,
                conversationMetricsConfiguration,
                creditDeductUseCase,
                MemoryExtractionPolicy(0),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("conversationThreshold")
    }
}
