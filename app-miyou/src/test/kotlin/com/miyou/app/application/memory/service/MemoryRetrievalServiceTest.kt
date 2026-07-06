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
import org.mockito.Mockito.times
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
    @DisplayName("반복 조회로 lastAccessedAt이 갱신된 오래된 메모리보다 최근 생성된 메모리를 우선한다")
    fun retrieveMemories_ranksRecentlyCreatedMemoryAboveStaleFrequentlyAccessedOne() {
        val sessionId = ConversationSessionFixture.createId()
        val now = Instant.now()
        val recent =
            Memory(
                "m-recent",
                sessionId,
                MemoryType.FACTUAL,
                "user ate ramen yesterday",
                0.6f,
                now,
                now,
                1,
            )
        // 90일 전 사건이지만 반복 조회로 lastAccessedAt만 계속 최신화된 상황 재현.
        val staleButFrequentlyAccessed =
            Memory(
                "m-stale",
                sessionId,
                MemoryType.FACTUAL,
                "user ate salad a while ago",
                0.6f,
                now.minusSeconds(90L * 24 * 3600),
                now,
                10,
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
                2
            ),
        ).thenReturn(Flux.just(staleButFrequentlyAccessed, recent))
        `when`(vectorMemoryPort.updateImportance(anyStringValue(), anyFloatValue(), anyValue(), anyIntValue()))
            .thenReturn(Mono.empty())

        StepVerifier
            .create(service.retrieveMemories(sessionId, "query", 1))
            .assertNext { result ->
                assertThat(result.factualMemories).hasSize(1)
                assertThat(result.factualMemories[0].id).isEqualTo("m-recent")
            }.verifyComplete()
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

    @Test
    @DisplayName("associativeHopEnabled면 1차 최상위 결과의 content로 2차 연상 검색을 수행해 결과를 병합한다")
    fun retrieveMemories_associativeHopEnabled_expandsFromTopCandidate() {
        val associativeService =
            MemoryRetrievalService(
                embeddingPort,
                vectorMemoryPort,
                ragQualityMetricsConfiguration,
                MemoryRetrievalPolicy(
                    0.05f,
                    0.3f,
                    associativeHopEnabled = true,
                    associativeHopTopK = 2,
                    associativeHopMinScore = 0.5f
                ),
            )
        val sessionId = ConversationSessionFixture.createId()
        val now = Instant.now()
        val trigger =
            Memory("m-ramen", sessionId, MemoryType.FACTUAL, "user ate ramen", 0.9f, now, now, 1)
        val other =
            Memory("m-other", sessionId, MemoryType.FACTUAL, "user likes tea", 0.4f, now, now, 1)
        val associative =
            Memory("m-friend", sessionId, MemoryType.EXPERIENTIAL, "user ate ramen with a friend", 0.5f, now, now, 1)
        val types = listOf(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL)

        `when`(embeddingPort.embed("query")).thenReturn(Mono.just(MemoryEmbedding.of("query", listOf(0.1f, 0.2f))))
        `when`(vectorMemoryPort.search(sessionId, listOf(0.1f, 0.2f), types, 0.3f, 6))
            .thenReturn(Flux.just(trigger, other))
        `when`(embeddingPort.embed("user ate ramen"))
            .thenReturn(Mono.just(MemoryEmbedding.of("user ate ramen", listOf(0.9f, 0.9f))))
        `when`(vectorMemoryPort.search(sessionId, listOf(0.9f, 0.9f), types, 0.3f, 2))
            .thenReturn(Flux.just(associative))
        `when`(vectorMemoryPort.updateImportance(anyStringValue(), anyFloatValue(), anyValue(), anyIntValue()))
            .thenReturn(Mono.empty())

        StepVerifier
            .create(associativeService.retrieveMemories(sessionId, "query", 3))
            .assertNext { result ->
                assertThat(result.factualMemories.map(Memory::id)).containsExactlyInAnyOrder("m-ramen", "m-other")
                assertThat(result.experientialMemories.map(Memory::id)).containsExactly("m-friend")
            }.verifyComplete()

        verify(vectorMemoryPort).search(sessionId, listOf(0.9f, 0.9f), types, 0.3f, 2)
    }

    @Test
    @DisplayName("associativeHopEnabled여도 1차 최상위 점수가 임계값 미만이면 2차 검색을 하지 않는다")
    fun retrieveMemories_associativeHopEnabled_skipsSecondSearchWhenTopScoreBelowThreshold() {
        val associativeService =
            MemoryRetrievalService(
                embeddingPort,
                vectorMemoryPort,
                ragQualityMetricsConfiguration,
                MemoryRetrievalPolicy(
                    0.05f,
                    0.3f,
                    associativeHopEnabled = true,
                    associativeHopTopK = 2,
                    associativeHopMinScore = 0.5f
                ),
            )
        val sessionId = ConversationSessionFixture.createId()
        val now = Instant.now()
        val weakCandidate =
            Memory("m-weak", sessionId, MemoryType.FACTUAL, "user mentioned something once", 0.2f, now, now, 1)

        `when`(embeddingPort.embed("query")).thenReturn(Mono.just(MemoryEmbedding.of("query", listOf(0.1f, 0.2f))))
        `when`(
            vectorMemoryPort.search(
                sessionId,
                listOf(0.1f, 0.2f),
                listOf(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL),
                0.3f,
                2
            ),
        ).thenReturn(Flux.just(weakCandidate))
        `when`(vectorMemoryPort.updateImportance(anyStringValue(), anyFloatValue(), anyValue(), anyIntValue()))
            .thenReturn(Mono.empty())

        StepVerifier
            .create(associativeService.retrieveMemories(sessionId, "query", 1))
            .assertNext { result -> assertThat(result.factualMemories.map(Memory::id)).containsExactly("m-weak") }
            .verifyComplete()

        verify(embeddingPort, times(1)).embed(anyStringValue())
        verify(vectorMemoryPort, times(1)).search(anyValue(), anyValue(), anyValue(), anyFloatValue(), anyIntValue())
    }

    @Test
    @DisplayName("associativeHopEnabled여도 2차 검색이 실패하면 1차 검색 결과로 대체한다")
    fun retrieveMemories_associativeHopEnabled_fallsBackToPrimaryOnSecondSearchFailure() {
        val associativeService =
            MemoryRetrievalService(
                embeddingPort,
                vectorMemoryPort,
                ragQualityMetricsConfiguration,
                MemoryRetrievalPolicy(
                    0.05f,
                    0.3f,
                    associativeHopEnabled = true,
                    associativeHopTopK = 2,
                    associativeHopMinScore = 0.3f
                ),
            )
        val sessionId = ConversationSessionFixture.createId()
        val now = Instant.now()
        val trigger = Memory("m-trigger", sessionId, MemoryType.FACTUAL, "user ate ramen", 0.9f, now, now, 1)
        val types = listOf(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL)

        `when`(embeddingPort.embed("query")).thenReturn(Mono.just(MemoryEmbedding.of("query", listOf(0.1f, 0.2f))))
        `when`(vectorMemoryPort.search(sessionId, listOf(0.1f, 0.2f), types, 0.3f, 2)).thenReturn(Flux.just(trigger))
        `when`(embeddingPort.embed("user ate ramen")).thenReturn(Mono.error(RuntimeException("embedding API 타임아웃")))
        `when`(vectorMemoryPort.updateImportance(anyStringValue(), anyFloatValue(), anyValue(), anyIntValue()))
            .thenReturn(Mono.empty())

        StepVerifier
            .create(associativeService.retrieveMemories(sessionId, "query", 1))
            .assertNext { result -> assertThat(result.factualMemories.map(Memory::id)).containsExactly("m-trigger") }
            .verifyComplete()
    }
}
