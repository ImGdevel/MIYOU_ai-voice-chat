package com.study.webflux.rag.application.memory.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.memory.model.MemoryType;
import com.study.webflux.rag.domain.memory.port.EmbeddingPort;
import com.study.webflux.rag.domain.memory.port.VectorMemoryPort;
import com.study.webflux.rag.infrastructure.memory.adapter.MemoryExtractionConfig;
import com.study.webflux.rag.infrastructure.monitoring.config.RagQualityMetricsConfiguration;
import reactor.core.publisher.Mono;

/**
 * 사용자 질의에 대해 벡터 메모리를 조회하고, 중요도/최신성 기준으로 정렬된 결과를 제공합니다.
 */
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

	/**
	 * 질의를 임베딩한 뒤 후보 메모리를 조회하고, 랭킹/그룹핑 후 접근 메트릭을 갱신합니다.
	 *
	 * @param personaId
	 *            페르소나 ID
	 * @param userId
	 *            사용자 ID
	 * @param query
	 *            검색 질의
	 * @param topK
	 *            반환할 상위 메모리 수
	 * @return 유형별(경험/사실)로 그룹화된 메모리 결과
	 */
	public Mono<MemoryRetrievalResult> retrieveMemories(PersonaId personaId,
		UserId userId,
		String query,
		int topK) {
		return embeddingPort.embed(query)
			.flatMap(
				embedding -> searchCandidateMemories(personaId, userId, embedding.vector(), topK))
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

	/**
	 * 하위 호환성을 위한 기존 메서드
	 */
	public Mono<MemoryRetrievalResult> retrieveMemories(UserId userId, String query, int topK) {
		return retrieveMemories(PersonaId.defaultPersona(), userId, query, topK);
	}

	/**
	 * 벡터 저장소에서 중요도 임계값을 만족하는 후보 메모리를 조회합니다.
	 */
	private Mono<List<Memory>> searchCandidateMemories(PersonaId personaId,
		UserId userId,
		List<Float> queryEmbedding,
		int topK) {
		List<MemoryType> types = List.of(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL);
		return vectorMemoryPort.search(personaId,
			userId,
			queryEmbedding,
			types,
			importanceThreshold,
			topK * CANDIDATE_MULTIPLIER).collectList();
	}

	/**
	 * 최신성과 중요도를 반영한 랭킹 점수로 정렬한 뒤 상위 K개만 반환합니다.
	 */
	private List<Memory> rankAndLimit(List<Memory> memories, int topK) {
		List<Memory> sorted = memories.stream()
			.sorted(Comparator.comparing((Memory memory) -> memory.calculateRankedScore(
				RECENCY_WEIGHT)).reversed())
			.toList();
		return sorted.size() > topK ? sorted.subList(0, topK) : sorted;
	}

	/**
	 * 메모리를 유형(경험적/사실 기반)별로 분류합니다.
	 */
	private MemoryRetrievalResult groupByType(List<Memory> memories) {
		List<Memory> experiential = memories.stream()
			.filter(m -> m.type() == MemoryType.EXPERIENTIAL).toList();

		List<Memory> factual = memories.stream().filter(m -> m.type() == MemoryType.FACTUAL)
			.toList();

		return MemoryRetrievalResult.of(experiential, factual);
	}

	/**
	 * 반환 대상 메모리의 중요도/접근시각/접근횟수를 갱신하고 결과를 동일한 형태로 재구성합니다.
	 */
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
