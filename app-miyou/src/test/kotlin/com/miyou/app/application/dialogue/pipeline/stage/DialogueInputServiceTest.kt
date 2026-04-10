package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.monitoring.service.PipelineTracer
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.domain.retrieval.port.RetrievalPort
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.support.anyIntValue
import com.miyou.app.support.anyValue
import com.miyou.app.support.eqValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
class DialogueInputServiceTest {
    @Mock
    private lateinit var retrievalPort: RetrievalPort

    @Mock
    private lateinit var conversationRepository: ConversationRepository

    @Mock
    private lateinit var pipelineTracer: PipelineTracer

    private lateinit var service: DialogueInputService

    @BeforeEach
    fun setUp() {
        service = DialogueInputService(retrievalPort, conversationRepository, pipelineTracer)
    }

    @Test
    @DisplayName("파이프라인 입력 준비 시 필요한 구성 요소를 모두 반환한다")
    fun prepareInputs_shouldReturnPipelineInputsWithAllComponents() {
        val session = ConversationSessionFixture.create()
        val query = "hello"
        val retrievalContext = RetrievalContext.empty(query)
        val memories = MemoryRetrievalResult.empty()
        val previousTurn =
            ConversationTurn
                .create(session.sessionId, "previous question")
                .withResponse("previous answer")

        `when`(pipelineTracer.traceRetrieval(anyValue())).thenAnswer { Mono.just(retrievalContext) }
        `when`(pipelineTracer.traceMemories(anyValue())).thenAnswer { Mono.just(memories) }
        `when`(conversationRepository.findRecent(eqValue(session.sessionId), anyIntValue()))
            .thenReturn(Flux.just(previousTurn))

        StepVerifier
            .create(service.prepareInputs(session, query))
            .assertNext { inputs ->
                assertThat(inputs.session).isEqualTo(session)
                assertThat(inputs.retrievalContext).isEqualTo(retrievalContext)
                assertThat(inputs.memories).isEqualTo(memories)
                assertThat(inputs.conversationContext).isNotNull
                assertThat(inputs.currentTurn).isNotNull
                assertThat(inputs.currentTurn.query).isEqualTo(query)
            }.verifyComplete()
    }

    @Test
    @DisplayName("이전 대화가 없으면 빈 대화 이력을 반환한다")
    fun prepareInputs_withEmptyHistory_shouldReturnEmptyContext() {
        val session = ConversationSessionFixture.create()
        val query = "first question"

        `when`(pipelineTracer.traceRetrieval(anyValue()))
            .thenAnswer { Mono.just(RetrievalContext.empty(query)) }
        `when`(pipelineTracer.traceMemories(anyValue()))
            .thenAnswer { Mono.just(MemoryRetrievalResult.empty()) }
        `when`(conversationRepository.findRecent(eqValue(session.sessionId), anyIntValue()))
            .thenReturn(Flux.empty())

        StepVerifier
            .create(service.prepareInputs(session, query))
            .assertNext { inputs ->
                assertThat(inputs.conversationContext.turns).isEmpty()
            }.verifyComplete()
    }

    @Test
    @DisplayName("검색 단계 오류를 그대로 전파한다")
    fun prepareInputs_withRetrievalError_shouldPropagateError() {
        val session = ConversationSessionFixture.create()
        val query = "failure case"

        `when`(pipelineTracer.traceRetrieval(anyValue()))
            .thenAnswer { Mono.error<RetrievalContext>(RuntimeException("retrieval failed")) }
        `when`(pipelineTracer.traceMemories(anyValue()))
            .thenAnswer { Mono.just(MemoryRetrievalResult.empty()) }
        `when`(conversationRepository.findRecent(eqValue(session.sessionId), anyIntValue()))
            .thenReturn(Flux.empty())

        StepVerifier
            .create(service.prepareInputs(session, query))
            .expectErrorMatches { error -> error.message?.contains("retrieval failed") == true }
            .verify()
    }

    @Test
    @DisplayName("검색과 메모리 조회를 함께 수행한다")
    fun prepareInputs_shouldExecuteRetrievalAndMemoryInParallel() {
        val session = ConversationSessionFixture.create()
        val query = "parallel"

        `when`(pipelineTracer.traceRetrieval(anyValue()))
            .thenAnswer { Mono.just(RetrievalContext.empty(query)) }
        `when`(pipelineTracer.traceMemories(anyValue()))
            .thenAnswer { Mono.just(MemoryRetrievalResult.empty()) }
        `when`(conversationRepository.findRecent(eqValue(session.sessionId), anyIntValue()))
            .thenReturn(Flux.empty())

        StepVerifier
            .create(service.prepareInputs(session, query))
            .assertNext { inputs -> assertThat(inputs).isNotNull() }
            .verifyComplete()

        verify(pipelineTracer).traceRetrieval(anyValue())
        verify(pipelineTracer).traceMemories(anyValue())
    }
}
