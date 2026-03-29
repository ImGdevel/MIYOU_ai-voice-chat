package com.miyou.app.application.memory.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.miyou.app.application.memory.policy.MemoryRetrievalPolicy;
import com.miyou.app.application.monitoring.port.RagQualityMetricsPort;
import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.memory.model.Memory;
import com.miyou.app.domain.memory.model.MemoryRetrievalResult;
import com.miyou.app.domain.memory.model.MemoryType;
import com.miyou.app.domain.memory.port.EmbeddingPort;
import com.miyou.app.domain.memory.port.MemoryRetrievalPort;
import com.miyou.app.domain.memory.port.VectorMemoryPort;
import reactor.core.publisher.Mono;

@Service
public class MemoryRetrievalService implements MemoryRetrievalPort {

	private static final float RECENCY_WEIGHT = 0.1f;
	private static final int CANDIDATE_MULTIPLIER = 2;

	private final EmbeddingPort embeddingPort;
	private final VectorMemoryPort vectorMemoryPort;
	private final RagQualityMetricsPort ragMetrics;
	private final float importanceBoost;
	private final float importanceThreshold;

	public MemoryRetrievalService(EmbeddingPort embeddingPort,
		VectorMemoryPort vectorMemoryPort,
		RagQualityMetricsPort ragMetrics,
		MemoryRetrievalPolicy policy) {
		this.embeddingPort = embeddingPort;
		this.vectorMemoryPort = vectorMemoryPort;
		this.ragMetrics = ragMetrics;
		this.importanceBoost = policy.importanceBoost();
		this.importanceThreshold = policy.importanceThreshold();
	}

	@Override
	public Mono<MemoryRetrievalResult> retrieveMemories(ConversationSessionId sessionId,
		String query,
		int topK) {
		return embeddingPort.embed(query)
			.flatMap(embedding -> searchCandidateMemories(sessionId, embedding.vector(), topK))
			.doOnNext(candidates -> {
				ragMetrics.recordMemoryCandidateCount(candidates.size());
			})
			.map(memories -> rankAndLimit(memories, topK))
			.doOnNext(ranked -> {
				int candidateCount = topK * CANDIDATE_MULTIPLIER;
				int filteredCount = Math.max(0, candidateCount - ranked.size());
				ragMetrics.recordMemoryFilteredCount(filteredCount);

				ranked.forEach(memory -> {
					if (memory.importance() != null) {
						ragMetrics.recordMemoryImportanceScore(memory.importance());
					}
				});
			})
			.map(this::groupByType)
			.flatMap(this::updateAccessMetrics);
	}

	private Mono<List<Memory>> searchCandidateMemories(ConversationSessionId sessionId,
		List<Float> queryEmbedding,
		int topK) {
		List<MemoryType> types = List.of(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL);
		return vectorMemoryPort.search(sessionId,
			queryEmbedding,
			types,
			importanceThreshold,
			topK * CANDIDATE_MULTIPLIER).collectList();
	}

	private List<Memory> rankAndLimit(List<Memory> memories, int topK) {
		List<Memory> sorted = memories.stream()
			.sorted(Comparator.comparing((Memory memory) -> memory.calculateRankedScore(
				RECENCY_WEIGHT)).reversed())
			.toList();
		return sorted.size() > topK ? sorted.subList(0, topK) : sorted;
	}

	private MemoryRetrievalResult groupByType(List<Memory> memories) {
		List<Memory> experiential = memories.stream()
			.filter(m -> m.type() == MemoryType.EXPERIENTIAL).toList();

		List<Memory> factual = memories.stream().filter(m -> m.type() == MemoryType.FACTUAL)
			.toList();

		return MemoryRetrievalResult.of(experiential, factual);
	}

	private Mono<MemoryRetrievalResult> updateAccessMetrics(MemoryRetrievalResult result) {
		List<Memory> memories = result.allMemories();
		if (memories.isEmpty()) {
			return Mono.just(result);
		}
		return reactor.core.publisher.Flux.fromIterable(memories)
			.flatMap(memory -> {
				Memory updated = memory.withAccess(importanceBoost);
				return vectorMemoryPort.updateImportance(updated.id(),
					updated.importance(),
					updated.lastAccessedAt(),
					updated.accessCount()).thenReturn(updated);
			})
			.collectList()
			.map(this::groupByType);
	}
}
