package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.monitoring.service.PipelineTracer
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.domain.retrieval.port.RetrievalPort
import com.miyou.app.fixture.ConversationSessionFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
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
    @DisplayName("입력 준비는 검색 컨텍스트, 메모리, 대화 이력을 모두 포함한다")
    fun prepareInputs_shouldReturnPipelineInputsWithAllComponents() {
        val session = ConversationSessionFixture.create()
        val query = "안녕하세요"
        val retrievalContext = RetrievalContext.empty(query)
        val memories = MemoryRetrievalResult.empty()
        val previousTurn =
            ConversationTurn
                .create(session.sessionId(), "이전 질문")
                .withResponse("이전 응답")

        `when`(pipelineTracer.traceRetrieval(any())).thenAnswer { Mono.just(retrievalContext) }
        `when`(pipelineTracer.traceMemories(any())).thenAnswer { Mono.just(memories) }
        `when`(conversationRepository.findRecent(eq(session.sessionId()), anyInt()))
            .thenReturn(Flux.just(previousTurn))

        StepVerifier
            .create(service.prepareInputs(session, query))
            .assertNext { inputs ->
                assertThat(inputs.session()).isEqualTo(session)
                assertThat(inputs.retrievalContext()).isEqualTo(retrievalContext)
                assertThat(inputs.memories()).isEqualTo(memories)
                assertThat(inputs.conversationContext()).isNotNull()
                assertThat(inputs.currentTurn()).isNotNull()
                assertThat(inputs.currentTurn().query()).isEqualTo(query)
            }.verifyComplete()
    }

    @Test
    @DisplayName("대화 이력이 없으면 빈 컨텍스트를 반환한다")
    fun prepareInputs_withEmptyHistory_shouldReturnEmptyContext() {
        val session = ConversationSessionFixture.create()
        val query = "첫 질문"

        `when`(pipelineTracer.traceRetrieval(any()))
            .thenAnswer { Mono.just(RetrievalContext.empty(query)) }
        `when`(pipelineTracer.traceMemories(any()))
            .thenAnswer { Mono.just(MemoryRetrievalResult.empty()) }
        `when`(conversationRepository.findRecent(eq(session.sessionId()), anyInt()))
            .thenReturn(Flux.empty())

        StepVerifier
            .create(service.prepareInputs(session, query))
            .assertNext { inputs ->
                assertThat(inputs.conversationContext().turns()).isEmpty()
            }.verifyComplete()
    }

    @Test
    @DisplayName("검색 단계 실패는 그대로 전파한다")
    fun prepareInputs_withRetrievalError_shouldPropagateError() {
        val session = ConversationSessionFixture.create()
        val query = "실패 테스트"

        `when`(pipelineTracer.traceRetrieval(any()))
            .thenAnswer { Mono.error<RetrievalContext>(RuntimeException("검색 실패")) }
        `when`(pipelineTracer.traceMemories(any()))
            .thenAnswer { Mono.just(MemoryRetrievalResult.empty()) }
        `when`(conversationRepository.findRecent(eq(session.sessionId()), anyInt()))
            .thenReturn(Flux.empty())

        StepVerifier
            .create(service.prepareInputs(session, query))
            .expectErrorMatches { error -> error.message?.contains("검색 실패") == true }
            .verify()
    }

    @Test
    @DisplayName("검색과 메모리 조회는 병렬로 실행한다")
    fun prepareInputs_shouldExecuteRetrievalAndMemoryInParallel() {
        val session = ConversationSessionFixture.create()
        val query = "병렬 테스트"

        `when`(pipelineTracer.traceRetrieval(any()))
            .thenAnswer { Mono.just(RetrievalContext.empty(query)) }
        `when`(pipelineTracer.traceMemories(any()))
            .thenAnswer { Mono.just(MemoryRetrievalResult.empty()) }
        `when`(conversationRepository.findRecent(eq(session.sessionId()), anyInt()))
            .thenReturn(Flux.empty())

        StepVerifier
            .create(service.prepareInputs(session, query))
            .assertNext { inputs -> assertThat(inputs).isNotNull() }
            .verifyComplete()

        verify(pipelineTracer).traceRetrieval(any())
        verify(pipelineTracer).traceMemories(any())
    }
}
