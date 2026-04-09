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

@Service
class MemoryRetrievalService(
    private val embeddingPort: EmbeddingPort,
    private val vectorMemoryPort: VectorMemoryPort,
    private val ragMetrics: RagQualityMetricsPort,
    policy: MemoryRetrievalPolicy,
) : MemoryRetrievalPort {
    private val importanceBoost = policy.importanceBoost
    private val importanceThreshold = policy.importanceThreshold

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
        return vectorMemoryPort
            .search(
                sessionId,
                queryEmbedding,
                types,
                importanceThreshold,
                topK * CANDIDATE_MULTIPLIER,
            ).collectList()
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
