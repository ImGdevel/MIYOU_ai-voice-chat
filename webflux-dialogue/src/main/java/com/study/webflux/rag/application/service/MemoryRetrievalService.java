package com.study.webflux.rag.application.service;

import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.model.memory.Memory;
import com.study.webflux.rag.domain.model.memory.MemoryRetrievalResult;
import com.study.webflux.rag.domain.model.memory.MemoryType;
import com.study.webflux.rag.domain.port.out.EmbeddingPort;
import com.study.webflux.rag.domain.port.out.VectorMemoryPort;
import com.study.webflux.rag.infrastructure.adapter.memory.MemoryExtractionConfig;
import reactor.core.publisher.Mono;

/**
 * MemoryRetrievalService는 메모리 검색 작업을 수행하는 서비스를 정의합니다.
 */
@Slf4j
@Service
public class MemoryRetrievalService {

	private final EmbeddingPort embeddingPort;
	private final VectorMemoryPort vectorMemoryPort;
	private final float importanceBoost; // 중요도 부스트
	private final float importanceThreshold; // 중요도 임계값
	private final float recencyWeight = 0.1f; // 레전시 가중치

	/**
	 * MemoryRetrievalService를 생성합니다.
	 *
	 * @param embeddingPort
	 *            임베딩 포트
	 * @param vectorMemoryPort
	 *            벡터 메모리 포트
	 * @param config
	 *            메모리 추출 설정
	 */
	public MemoryRetrievalService(EmbeddingPort embeddingPort,
		VectorMemoryPort vectorMemoryPort,
		MemoryExtractionConfig config) {
		this.embeddingPort = embeddingPort;
		this.vectorMemoryPort = vectorMemoryPort;
		this.importanceBoost = config.importanceBoost();
		this.importanceThreshold = config.importanceThreshold();
	}

	/**
	 * 메모리를 검색합니다.
	 *
	 * @param query
	 *            검색 쿼리
	 * @param topK
	 *            검색할 상위 메모리 개수
	 * @return Mono<MemoryRetrievalResult> 메모리 검색 결과
	 */
	public Mono<MemoryRetrievalResult> retrieveMemories(String query, int topK) {
		return embeddingPort.embed(query).flatMap(embedding -> {
			List<MemoryType> types = List.of(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL);

			return vectorMemoryPort.search(embedding.vector(), types, importanceThreshold, topK * 2)
				.collectList()
				.map(memories -> {
					return memories.stream()
						.sorted(Comparator
							.comparing((Memory m) -> m.calculateRankedScore(recencyWeight))
							.reversed())
						.toList();
				})
				.map(sorted -> sorted.size() > topK ? sorted.subList(0, topK) : sorted)
				.map(this::groupByType);
		}).flatMap(this::updateAccessMetrics);
	}

	/**
	 * 메모리를 타입별로 그룹화합니다.
	 *
	 * @param memories
	 *            검색된 메모리 목록
	 * @return MemoryRetrievalResult 메모리 검색 결과
	 */
	private MemoryRetrievalResult groupByType(List<Memory> memories) {
		List<Memory> experiential = memories.stream()
			.filter(m -> m.type() == MemoryType.EXPERIENTIAL).toList();

		List<Memory> factual = memories.stream().filter(m -> m.type() == MemoryType.FACTUAL)
			.toList();

		return MemoryRetrievalResult.of(experiential, factual);
	}

	/**
	 * 메모리 접근 메트릭을 업데이트합니다.
	 *
	 * @param result
	 *            메모리 검색 결과
	 * @return Mono<MemoryRetrievalResult> 업데이트된 메모리 검색 결과
	 */
	private Mono<MemoryRetrievalResult> updateAccessMetrics(MemoryRetrievalResult result) {
		return Mono.just(result.allMemories())
			.flatMapMany(memories -> reactor.core.publisher.Flux.fromIterable(memories))
			.flatMap(memory -> {
				Memory updated = memory.withAccess(importanceBoost);
				return vectorMemoryPort.updateImportance(updated.id(),
					updated.importance(),
					updated.lastAccessedAt(),
					updated.accessCount()).thenReturn(updated);
			}).collectList().map(updatedMemories -> {
				List<Memory> experiential = updatedMemories.stream()
					.filter(m -> m.type() == MemoryType.EXPERIENTIAL).toList();
				List<Memory> factual = updatedMemories.stream()
					.filter(m -> m.type() == MemoryType.FACTUAL).toList();
				return MemoryRetrievalResult.of(experiential, factual);
			});
	}
}
