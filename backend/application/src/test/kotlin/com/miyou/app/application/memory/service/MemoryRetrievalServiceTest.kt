package com.miyou.app.application.memory.service

import com.miyou.app.application.memory.policy.MemoryRetrievalPolicy
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryEmbedding
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.EmbeddingPort
import com.miyou.app.domain.memory.port.VectorMemoryPort
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.monitoring.port.RagQualityMetricsPort
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
        // given - 테스트용 세션 ID 및 감쇠 이전 상태의 서로 다른 중요도를 가진 3개의 메모리 준비
        val sessionId = ConversationSessionFixture.createId().value
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

        // 임베딩 및 벡터 검색 결과 모킹
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

        // when - 최대 2개의 메모리만 조회하도록 요청
        StepVerifier
            .create(service.retrieveMemories(sessionId, "query", 2))
            .assertNext { result ->
                // then - 상위 2개의 메모리(top, second)만 반환되고 dropped는 제외되었는지 검증
                assertThat(result.experientialMemories).hasSize(1)
                assertThat(result.experientialMemories[0].id).isEqualTo("m-top")
                assertThat(result.factualMemories).hasSize(1)
                assertThat(result.factualMemories[0].id).isEqualTo("m-second")
            }.verifyComplete()

        // then - 검색 결과에 포함된 상위 2개 메모리에 대해서만 중요도 및 접근 카운트가 갱신되었는지 검증
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
        // given - 테스트용 세션 ID 및 최근 생성 메모리와, 반복 조회된 오래된 메모리 설정
        val sessionId = ConversationSessionFixture.createId().value
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

        // 임베딩 및 검색 모킹
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

        // when - 최대 1개 조회 조건으로 조회 실행
        StepVerifier
            .create(service.retrieveMemories(sessionId, "query", 1))
            .assertNext { result ->
                // then - 최근 생성 메모리가 오래된 메모리보다 랭킹 가중치 공식에 의해 더 높은 우선순위로 반환되는지 확인
                assertThat(result.factualMemories).hasSize(1)
                assertThat(result.factualMemories[0].id).isEqualTo("m-recent")
            }.verifyComplete()
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 메모리 결과를 반환한다")
    fun retrieveMemories_shouldReturnEmptyWithoutUpdateWhenSearchIsEmpty() {
        // given - 세션 ID 설정
        val sessionId = ConversationSessionFixture.createId().value

        // 임베딩 결과 모킹 및 검색 결과 빈 Flux 반환 모킹
        `when`(embeddingPort.embed("query")).thenReturn(
            Mono.just(MemoryEmbedding.of("query", listOf(0.1f, 0.2f))),
        )
        `when`(vectorMemoryPort.search(eqValue(sessionId), anyValue(), anyValue(), anyFloatValue(), anyIntValue()))
            .thenReturn(Flux.empty())

        // when - 조회 실행
        StepVerifier
            .create(service.retrieveMemories(sessionId, "query", 3))
            .assertNext { result ->
                // then - 빈 결과 반환 검증
                assertThat(result.isEmpty()).isTrue()
            }.verifyComplete()

        // 중요도 업데이트 포트가 호출되지 않아야 함 검증
        verify(vectorMemoryPort, never()).updateImportance(anyStringValue(), anyFloatValue(), anyValue(), anyIntValue())
    }

    @Test
    @DisplayName("associativeHopEnabled면 1차 최상위 결과의 content로 2차 연상 검색을 수행해 결과를 병합한다")
    fun retrieveMemories_associativeHopEnabled_expandsFromTopCandidate() {
        // given - 연상 홉 검색 기능이 활성화된 서비스 정책 설정
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
        val sessionId = ConversationSessionFixture.createId().value
        val now = Instant.now()
        val trigger =
            Memory("m-ramen", sessionId, MemoryType.FACTUAL, "user ate ramen", 0.9f, now, now, 1)
        val other =
            Memory("m-other", sessionId, MemoryType.FACTUAL, "user likes tea", 0.4f, now, now, 1)
        val associative =
            Memory("m-friend", sessionId, MemoryType.EXPERIENTIAL, "user ate ramen with a friend", 0.5f, now, now, 1)
        val types = listOf(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL)

        // 1차 임베딩 및 검색 모킹
        `when`(embeddingPort.embed("query")).thenReturn(Mono.just(MemoryEmbedding.of("query", listOf(0.1f, 0.2f))))
        `when`(vectorMemoryPort.search(sessionId, listOf(0.1f, 0.2f), types, 0.3f, 6))
            .thenReturn(Flux.just(trigger, other))

        // 2차 연상 임베딩 및 검색 모킹 (1차 최상위 후보인 "user ate ramen"의 컨텐츠로 재검색)
        `when`(embeddingPort.embed("user ate ramen"))
            .thenReturn(Mono.just(MemoryEmbedding.of("user ate ramen", listOf(0.9f, 0.9f))))
        `when`(vectorMemoryPort.search(sessionId, listOf(0.9f, 0.9f), types, 0.3f, 2))
            .thenReturn(Flux.just(associative))
        `when`(vectorMemoryPort.updateImportance(anyStringValue(), anyFloatValue(), anyValue(), anyIntValue()))
            .thenReturn(Mono.empty())

        // when - 연상 검색을 포함하여 3개 제한으로 조회 실행
        StepVerifier
            .create(associativeService.retrieveMemories(sessionId, "query", 3))
            .assertNext { result ->
                // then - 1차 결과("m-ramen", "m-other")와 2차 연상 결과("m-friend")가 모두 병합되어 반환되는지 확인
                assertThat(result.factualMemories.map(Memory::id)).containsExactlyInAnyOrder("m-ramen", "m-other")
                assertThat(result.experientialMemories.map(Memory::id)).containsExactly("m-friend")
            }.verifyComplete()

        // then - 2차 연상 검색용 쿼리가 실행되었는지 검증
        verify(vectorMemoryPort).search(sessionId, listOf(0.9f, 0.9f), types, 0.3f, 2)
    }

    @Test
    @DisplayName("associativeHopEnabled여도 1차 최상위 점수가 임계값 미만이면 2차 검색을 하지 않는다")
    fun retrieveMemories_associativeHopEnabled_skipsSecondSearchWhenTopScoreBelowThreshold() {
        // given - 연상 홉 기능이 활성화되었으나 최소 점수 기준이 높은 정책(0.5f) 설정
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
        val sessionId = ConversationSessionFixture.createId().value
        val now = Instant.now()
        // 1차 검색 후보의 중요도가 0.2f로 임계값(0.5f)보다 낮음
        val weakCandidate =
            Memory("m-weak", sessionId, MemoryType.FACTUAL, "user mentioned something once", 0.2f, now, now, 1)

        // 1차 검색 결과 모킹
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

        // when - 조회 실행
        StepVerifier
            .create(associativeService.retrieveMemories(sessionId, "query", 1))
            .assertNext { result -> assertThat(result.factualMemories.map(Memory::id)).containsExactly("m-weak") }
            .verifyComplete()

        // then - 2차 검색을 위한 임베딩 및 검색 포트가 호출되지 않고 1차만 실행되었는지 검증 (각각 1번씩 호출)
        verify(embeddingPort, times(1)).embed(anyStringValue())
        verify(vectorMemoryPort, times(1)).search(anyValue(), anyValue(), anyValue(), anyFloatValue(), anyIntValue())
    }

    @Test
    @DisplayName("associativeHopEnabled여도 2차 검색이 실패하면 1차 검색 결과로 대체한다")
    fun retrieveMemories_associativeHopEnabled_fallsBackToPrimaryOnSecondSearchFailure() {
        // given - 연상 홉 기능이 활성화된 정책
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
        val sessionId = ConversationSessionFixture.createId().value
        val now = Instant.now()
        val trigger = Memory("m-trigger", sessionId, MemoryType.FACTUAL, "user ate ramen", 0.9f, now, now, 1)
        val types = listOf(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL)

        // 1차 검색 성공 모킹, 2차 연상 검색 임베딩 호출 시 예외 발생 모킹
        `when`(embeddingPort.embed("query")).thenReturn(Mono.just(MemoryEmbedding.of("query", listOf(0.1f, 0.2f))))
        `when`(vectorMemoryPort.search(sessionId, listOf(0.1f, 0.2f), types, 0.3f, 2)).thenReturn(Flux.just(trigger))
        `when`(embeddingPort.embed("user ate ramen")).thenReturn(Mono.error(RuntimeException("embedding API 타임아웃")))
        `when`(vectorMemoryPort.updateImportance(anyStringValue(), anyFloatValue(), anyValue(), anyIntValue()))
            .thenReturn(Mono.empty())

        // when - 조회 실행
        StepVerifier
            .create(associativeService.retrieveMemories(sessionId, "query", 1))
            .assertNext { result ->
                // then - 2차 연상 검색 에러 발생 시 부드럽게 1차 검색 결과("m-trigger")만 반환되는지 검증 (Fallback 처리)
                assertThat(result.factualMemories.map(Memory::id)).containsExactly("m-trigger")
            }.verifyComplete()
    }
}
