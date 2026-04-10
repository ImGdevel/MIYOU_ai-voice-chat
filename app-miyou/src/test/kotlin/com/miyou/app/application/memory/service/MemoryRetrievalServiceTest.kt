package com.miyou.app.application.memory.service

import com.miyou.app.application.memory.policy.MemoryRetrievalPolicy
import com.miyou.app.application.monitoring.port.RagQualityMetricsPort
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryEmbedding
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.EmbeddingPort
import com.miyou.app.domain.memory.port.VectorMemoryPort
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.support.anyFloatValue
import com.miyou.app.support.anyIntValue
import com.miyou.app.support.anyStringValue
import com.miyou.app.support.anyValue
import com.miyou.app.support.eqValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class MemoryRetrievalServiceTest {
    @Mock
    private lateinit var embeddingPort: EmbeddingPort

    @Mock
    private lateinit var vectorMemoryPort: VectorMemoryPort

    @Mock
    private lateinit var ragQualityMetricsConfiguration: RagQualityMetricsPort

    private lateinit var service: MemoryRetrievalService

    @BeforeEach
    fun setUp() {
        service =
            MemoryRetrievalService(
                embeddingPort,
                vectorMemoryPort,
                ragQualityMetricsConfiguration,
                MemoryRetrievalPolicy(0.05f, 0.3f),
            )
    }

    @Test
    @DisplayName("메모리를 점수순으로 제한해 조회하고 접근 지표를 갱신한다")
    fun retrieveMemories_shouldRankLimitAndUpdateAccessMetrics() {
        val sessionId = ConversationSessionFixture.createId()
        val now = Instant.now()
        val top =
            Memory(
                "m-top",
                sessionId,
                MemoryType.EXPERIENTIAL,
                "user likes sushi",
                0.95f,
                now.minusSeconds(100),
                now,
                3,
            )
        val second =
            Memory(
                "m-second",
                sessionId,
                MemoryType.FACTUAL,
                "user is a developer",
                0.70f,
                now.minusSeconds(100),
                now,
                2,
            )
        val dropped =
            Memory(
                "m-dropped",
                sessionId,
                MemoryType.FACTUAL,
                "user owns a cat",
                0.10f,
                now.minusSeconds(100),
                now,
                1,
            )

        `when`(embeddingPort.embed("query")).thenReturn(
            Mono.just(MemoryEmbedding.of("query", listOf(0.1f, 0.2f))),
        )
        `when`(
            vectorMemoryPort.search(
                sessionId,
                listOf(0.1f, 0.2f),
                listOf(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL),
                0.3f,
                4,
            ),
        ).thenReturn(Flux.just(top, second, dropped))
        `when`(vectorMemoryPort.updateImportance(anyStringValue(), anyFloatValue(), anyValue(), anyIntValue()))
            .thenReturn(Mono.empty())

        StepVerifier
            .create(service.retrieveMemories(sessionId, "query", 2))
            .assertNext { result ->
                assertThat(result.experientialMemories).hasSize(1)
                assertThat(result.experientialMemories[0].id).isEqualTo("m-top")
                assertThat(result.factualMemories).hasSize(1)
                assertThat(result.factualMemories[0].id).isEqualTo("m-second")
            }.verifyComplete()

        verify(vectorMemoryPort).updateImportance(eqValue("m-top"), anyFloatValue(), anyValue(), eqValue(4))
        verify(vectorMemoryPort).updateImportance(eqValue("m-second"), anyFloatValue(), anyValue(), eqValue(3))
        verify(
            vectorMemoryPort,
            never()
        ).updateImportance(eqValue("m-dropped"), anyFloatValue(), anyValue(), anyIntValue())
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 메모리 결과를 반환한다")
    fun retrieveMemories_shouldReturnEmptyWithoutUpdateWhenSearchIsEmpty() {
        val sessionId = ConversationSessionFixture.createId()

        `when`(embeddingPort.embed("query")).thenReturn(
            Mono.just(MemoryEmbedding.of("query", listOf(0.1f, 0.2f))),
        )
        `when`(vectorMemoryPort.search(eqValue(sessionId), anyValue(), anyValue(), anyFloatValue(), anyIntValue()))
            .thenReturn(Flux.empty())

        StepVerifier
            .create(service.retrieveMemories(sessionId, "query", 3))
            .assertNext { result -> assertThat(result.isEmpty()).isTrue() }
            .verifyComplete()

        verify(vectorMemoryPort, never()).updateImportance(anyStringValue(), anyFloatValue(), anyValue(), anyIntValue())
    }
}
