package com.miyou.app.application.memory.service

import com.miyou.app.application.memory.policy.MemoryRetrievalPolicy
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.EmbeddingPort
import com.miyou.app.domain.memory.port.MemoryRetrievalPort
import com.miyou.app.domain.memory.port.VectorMemoryPort
import com.miyou.app.monitoring.port.RagQualityMetricsPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import kotlin.math.max

/**
 * 메모리 검색 서비스.
 *
 * 사용자 쿼리 임베딩 → 벡터 유사도 검색 → 중요도/최신성 기반 랭킹 → 접근 메트릭 갱신.
 */
@Service
class MemoryRetrievalService(
    private val embeddingPort: EmbeddingPort,
    private val vectorMemoryPort: VectorMemoryPort,
    private val ragMetrics: RagQualityMetricsPort,
    policy: MemoryRetrievalPolicy,
) : MemoryRetrievalPort {
    private val logger = KotlinLogging.logger {}
    private val importanceBoost = policy.importanceBoost
    private val importanceThreshold = policy.importanceThreshold
    private val associativeHopEnabled = policy.associativeHopEnabled
    private val associativeHopTopK = policy.associativeHopTopK
    private val associativeHopMinScore = policy.associativeHopMinScore

    /**
     * 관련 메모리 검색.
     *
     * 경험적 메모리(사건, 대화) + 사실 메모리(선호도, 정보) 결합 반환.
     * 임베딩 기반 벡터 유사도 + 중요도/최신성 스코어로 상위 K개 순위 결정.
     *
     * @param sessionId 사용자 세션 ID
     * @param query 검색 쿼리
     * @param topK 반환할 메모리 개수
     * @return 분류된 메모리 검색 결과
     */
    override fun retrieveMemories(
        sessionId: String,
        query: String,
        topK: Int,
    ): Mono<MemoryRetrievalResult> =
        embeddingPort
            .embed(query)
            .flatMap { embedding ->
                searchCandidateMemories(sessionId, embedding.vector, topK)
            }.doOnNext { candidates ->
                ragMetrics.recordMemoryCandidateCount(candidates.size)
            }.map { memories -> rankAndLimit(memories, topK) }
            .doOnNext { ranked ->
                val candidateCount = topK * CANDIDATE_MULTIPLIER
                val filteredCount = max(0, candidateCount - ranked.size)
                ragMetrics.recordMemoryFilteredCount(filteredCount)
                ranked.forEach { memory ->
                    memory.importance?.let { ragMetrics.recordMemoryImportanceScore(it.toDouble()) }
                }
            }.map(this::groupByType)
            .flatMap(this::updateAccessMetrics)

    /**
     * 세션에 매칭되는 후보 메모리 목록을 검색합니다.
     *
     * 1차적으로 벡터 유사도가 높은 메모리들을 가져오며, 연상 검색이 활성화되어 있을 경우 2차 연상 검색을 수행합니다.
     */
    private fun searchCandidateMemories(
        sessionId: String,
        queryEmbedding: List<Float>,
        topK: Int,
    ): Mono<List<Memory>> {
        val types = listOf(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL)
        val primary =
            vectorMemoryPort
                .search(
                    sessionId,
                    queryEmbedding,
                    types,
                    importanceThreshold,
                    topK * CANDIDATE_MULTIPLIER,
                ).collectList()

        if (!associativeHopEnabled) {
            return primary
        }
        return primary.flatMap { candidates -> expandAssociatively(sessionId, types, candidates) }
    }

    /**
     * 연상 기반 2차 검색(Associative Retrieval Hop)을 수행합니다.
     *
     * 1차 검색 결과 중 최상위 점수(최신성 감쇠가 반영된 스코어)가 연상 임계치([associativeHopMinScore]) 이상인
     * 단 하나의 대표 메모리를 선정하여, 해당 메모리의 내용을 임베딩한 뒤 2차 유사도 검색을 수행합니다.
     * 이를 통해 연쇄적으로 연관 있는 메모리들을 추가 확보합니다.
     */
    private fun expandAssociatively(
        sessionId: String,
        types: List<MemoryType>,
        candidates: List<Memory>,
    ): Mono<List<Memory>> {
        val trigger =
            candidates
                .sortedByDescending { it.calculateRankedScore(RECENCY_WEIGHT) }
                .firstOrNull { it.calculateRankedScore(RECENCY_WEIGHT) >= associativeHopMinScore }
                ?: return Mono.just(candidates)

        return embeddingPort
            .embed(trigger.content)
            .flatMap { embedding ->
                vectorMemoryPort
                    .search(sessionId, embedding.vector, types, importanceThreshold, associativeHopTopK)
                    .collectList()
            }.map { associative -> mergeDedup(candidates, associative) }
            .onErrorResume { error ->
                logger.warn(error) { "연상 기반 2차 검색 실패 - 1차 검색 결과로 대체" }
                Mono.just(candidates)
            }
    }

    private fun mergeDedup(
        primary: List<Memory>,
        additional: List<Memory>,
    ): List<Memory> {
        val seenIds = primary.mapNotNull { it.id }.toSet()
        return primary + additional.filter { it.id !in seenIds }
    }

    /**
     * 검색된 후보군에 대해 최신성 및 중요도를 반영한 랭킹 스코어로 정렬한 뒤, 상위 K개로 제한합니다.
     */
    private fun rankAndLimit(
        memories: List<Memory>,
        topK: Int,
    ): List<Memory> {
        val sorted = memories.sortedByDescending { memory -> memory.calculateRankedScore(RECENCY_WEIGHT) }
        return sorted.take(topK)
    }

    /**
     * 랭킹된 메모리를 성격에 따라 경험적 메모리(Experiential)와 사실적 메모(Factual)로 그룹화합니다.
     */
    private fun groupByType(memories: List<Memory>): MemoryRetrievalResult {
        val experiential = memories.filter { it.type == MemoryType.EXPERIENTIAL }
        val factual = memories.filter { it.type == MemoryType.FACTUAL }
        return MemoryRetrievalResult.of(experiential, factual)
    }

    /**
     * 검색 결과를 반환하기 전, 검색에 기여한 메모리들의 접근 일시 및 접근 횟수를 업데이트합니다.
     * 이 때 중요도에는 가중치([importanceBoost])가 부스팅됩니다.
     */
    private fun updateAccessMetrics(result: MemoryRetrievalResult): Mono<MemoryRetrievalResult> {
        val memories = result.allMemories()
        if (memories.isEmpty()) {
            return Mono.just(result)
        }
        return reactor.core.publisher.Flux
            .fromIterable(memories)
            .flatMap { memory ->
                val updated = memory.withAccess(importanceBoost)
                vectorMemoryPort
                    .updateImportance(
                        requireNotNull(updated.id),
                        requireNotNull(updated.importance),
                        requireNotNull(updated.lastAccessedAt),
                        requireNotNull(updated.accessCount),
                    ).thenReturn(updated)
            }.collectList()
            .map(this::groupByType)
    }

    private companion object {
        const val RECENCY_WEIGHT = 0.1f
        const val CANDIDATE_MULTIPLIER = 2
    }
}
