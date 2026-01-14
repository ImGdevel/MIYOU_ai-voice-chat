package com.study.webflux.rag.application.memory.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.memory.model.MemoryType;
import com.study.webflux.rag.domain.memory.port.EmbeddingPort;
import com.study.webflux.rag.domain.memory.port.VectorMemoryPort;
import com.study.webflux.rag.infrastructure.memory.adapter.MemoryExtractionConfig;
import com.study.webflux.rag.infrastructure.monitoring.config.RagQualityMetricsConfiguration;
import reactor.core.publisher.Mono;

@Service
public class MemoryRetrievalService {

	private static final float RECENCY_WEIGHT = 0.1f;
	private static final int CANDIDATE_MULTIPLIER = 2;

	private final EmbeddingPort embeddingPort;
	private final VectorMemoryPort vectorMemoryPort;
	private final RagQualityMetricsConfiguration ragMetrics;
	private final float importanceBoost;
	private final float importanceThreshold;

	public MemoryRetrievalService(EmbeddingPort embeddingPort,
		VectorMemoryPort vectorMemoryPort,
		RagQualityMetricsConfiguration ragMetrics,
		MemoryExtractionConfig config) {
		this.embeddingPort = embeddingPort;
		this.vectorMemoryPort = vectorMemoryPort;
		this.ragMetrics = ragMetrics;
		this.importanceBoost = config.importanceBoost();
		this.importanceThreshold = config.importanceThreshold();
	}

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
