package com.miyou.app.application.memory.service

import com.miyou.app.application.memory.policy.MemoryRetrievalPolicy
import com.miyou.app.application.monitoring.port.RagQualityMetricsPort
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.EmbeddingPort
import com.miyou.app.domain.memory.port.MemoryRetrievalPort
import com.miyou.app.domain.memory.port.VectorMemoryPort
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
        sessionId: ConversationSessionId,
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

    private fun searchCandidateMemories(
        sessionId: ConversationSessionId,
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
     * 연상 기반 2차 검색. 1차 검색 결과 중 랭킹 스코어가 associativeHopMinScore 이상인
     * 최상위 1개만 2차 쿼리로 재사용한다 - 약한 1차 매칭에서 연쇄되는 걸 막기 위함.
     * 연상으로 끌려온 메모리도 우회 없이 이후 동일한 rankAndLimit을 거친다.
     */
    private fun expandAssociatively(
        sessionId: ConversationSessionId,
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
    }

    private fun mergeDedup(
        primary: List<Memory>,
        additional: List<Memory>,
    ): List<Memory> {
        val seenIds = primary.mapNotNull { it.id }.toSet()
        return primary + additional.filter { it.id !in seenIds }
    }

    private fun rankAndLimit(
        memories: List<Memory>,
        topK: Int,
    ): List<Memory> {
        val sorted = memories.sortedByDescending { memory -> memory.calculateRankedScore(RECENCY_WEIGHT) }
        return sorted.take(topK)
    }

    private fun groupByType(memories: List<Memory>): MemoryRetrievalResult {
        val experiential = memories.filter { it.type == MemoryType.EXPERIENTIAL }
        val factual = memories.filter { it.type == MemoryType.FACTUAL }
        return MemoryRetrievalResult.of(experiential, factual)
    }

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
