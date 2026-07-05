package com.miyou.app.application.memory.service

import com.miyou.app.application.monitoring.port.MemoryExtractionMetricsPort
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.domain.memory.model.ExtractedMemory
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryEmbedding
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.ConversationCounterPort
import com.miyou.app.domain.memory.port.EmbeddingPort
import com.miyou.app.domain.memory.port.MemoryExtractionPort
import com.miyou.app.domain.memory.port.VectorMemoryPort
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.support.anyValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

private const val CONVERSATION_THRESHOLD = 5

@ExtendWith(MockitoExtension::class)
class MemoryExtractionServiceTest {
    @Mock
    private lateinit var conversationRepository: ConversationRepository

    @Mock
    private lateinit var counterPort: ConversationCounterPort

    @Mock
    private lateinit var extractionPort: MemoryExtractionPort

    @Mock
    private lateinit var embeddingPort: EmbeddingPort

    @Mock
    private lateinit var vectorMemoryPort: VectorMemoryPort

    @Mock
    private lateinit var retrievalService: MemoryRetrievalService

    @Mock
    private lateinit var extractionMetrics: MemoryExtractionMetricsPort

    private lateinit var service: MemoryExtractionService

    @BeforeEach
    fun setUp() {
        service =
            MemoryExtractionService(
                conversationRepository,
                counterPort,
                extractionPort,
                embeddingPort,
                vectorMemoryPort,
                retrievalService,
                extractionMetrics,
                CONVERSATION_THRESHOLD,
            )
    }

    @Test
    @DisplayName("임계값 미달 턴이면 추출을 트리거하지 않는다")
    fun checkAndExtract_belowThreshold_skipsExtraction() {
        val sessionId = ConversationSessionFixture.createId()
        `when`(counterPort.get(sessionId)).thenReturn(Mono.just(3L))

        StepVerifier.create(service.checkAndExtract(sessionId)).verifyComplete()

        verifyNoInteractions(extractionPort)
        verify(vectorMemoryPort, never()).upsert(anyValue(), anyValue())
    }

    @Test
    @DisplayName("임계값 도달 턴이면 reasoning이 있어도 Memory에는 content/importance만 저장된다")
    fun checkAndExtract_atThreshold_savesExtractedMemoryWithoutReasoning() {
        val sessionId = ConversationSessionFixture.createId()
        val turn = ConversationTurn.create(sessionId, "나는 커피를 좋아해")
        val extracted =
            ExtractedMemory(
                sessionId,
                MemoryType.FACTUAL,
                "사용자는 커피를 좋아한다",
                0.7f,
                "선호도 관련 발언이라 근거가 명확함",
            )

        `when`(counterPort.get(sessionId)).thenReturn(Mono.just(5L))
        `when`(conversationRepository.findRecent(sessionId, CONVERSATION_THRESHOLD))
            .thenReturn(Flux.just(turn))
        `when`(retrievalService.retrieveMemories(sessionId, turn.query, 10))
            .thenReturn(Mono.just(MemoryRetrievalResult.empty()))
        `when`(extractionPort.extractMemories(anyValue())).thenReturn(Flux.just(extracted))
        `when`(embeddingPort.embed(extracted.content))
            .thenReturn(Mono.just(MemoryEmbedding(extracted.content, listOf(0.1f, 0.2f))))

        var savedMemory: Memory? = null
        `when`(vectorMemoryPort.upsert(anyValue(), anyValue())).thenAnswer { invocation ->
            savedMemory = invocation.getArgument(0)
            Mono.just(invocation.getArgument<Memory>(0))
        }

        StepVerifier.create(service.checkAndExtract(sessionId)).verifyComplete()

        assertThat(savedMemory?.content).isEqualTo(extracted.content)
        assertThat(savedMemory?.type).isEqualTo(MemoryType.FACTUAL)
        assertThat(savedMemory?.importance).isCloseTo(0.7f, Offset.offset(0.001f))

        verify(extractionMetrics).recordExtractionTriggered()
        verify(extractionMetrics).recordExtractionSuccess(1)
        verify(extractionMetrics).recordExtractedMemoryType("FACTUAL", 1)
    }
}
