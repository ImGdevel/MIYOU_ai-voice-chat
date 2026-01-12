package com.study.webflux.rag.infrastructure.monitoring.config;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * RAG 품질 모니터링 메트릭을 제공합니다.
 *
 * <p>
 * Phase 1B: RAG Quality Monitoring - Vector 검색 유사도 점수 - 메모리 중요도 분포 - 메모리 필터링 비율 - 문서 검색 관련성 점수
 */
@Component
public class RagQualityMetricsConfiguration {

	private final MeterRegistry meterRegistry;

	// Counters
	private final Counter memoryCandidateCounter;
	private final Counter memoryFilteredCounter;

	// Distribution Summaries (for histogram)
	private final DistributionSummary memorySimilarityScore;
	private final DistributionSummary memoryImportanceScore;
	private final DistributionSummary documentRelevanceScore;

	public RagQualityMetricsConfiguration(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;

		// Memory candidate count
		this.memoryCandidateCounter = Counter.builder("rag.memory.candidate.count")
			.description("Number of candidate memories before filtering")
			.register(meterRegistry);

		// Memory filtered count
		this.memoryFilteredCounter = Counter.builder("rag.memory.filtered.count")
			.description("Number of memories filtered out")
			.register(meterRegistry);

		// Memory similarity score distribution
		this.memorySimilarityScore = DistributionSummary.builder("rag.memory.similarity.score")
			.description("Vector similarity score for retrieved memories")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);

		// Memory importance score distribution
		this.memoryImportanceScore = DistributionSummary.builder("rag.memory.importance")
			.description("Importance score for retrieved memories")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);

		// Document relevance score distribution
		this.documentRelevanceScore = DistributionSummary.builder("rag.document.relevance.score")
			.description("Relevance score for retrieved documents")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);
	}

	/**
	 * 메모리 후보 개수를 기록합니다.
	 */
	public void recordMemoryCandidateCount(int count) {
		memoryCandidateCounter.increment(count);
	}

	/**
	 * 필터링된 메모리 개수를 기록합니다.
	 */
	public void recordMemoryFilteredCount(int count) {
		memoryFilteredCounter.increment(count);
	}

	/**
	 * 메모리 유사도 점수를 기록합니다.
	 */
	public void recordMemorySimilarityScore(double score) {
		memorySimilarityScore.record(score);
	}

	/**
	 * 메모리 중요도 점수를 기록합니다.
	 */
	public void recordMemoryImportanceScore(double importance) {
		memoryImportanceScore.record(importance);
	}

	/**
	 * 문서 관련성 점수를 기록합니다.
	 */
	public void recordDocumentRelevanceScore(double score) {
		documentRelevanceScore.record(score);
	}
}
