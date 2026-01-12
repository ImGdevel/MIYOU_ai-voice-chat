package com.study.webflux.rag.infrastructure.monitoring.config;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 메모리 추출 품질 메트릭을 제공합니다.
 *
 * <p>
 * Phase 1B: RAG Quality Monitoring - 메모리 추출 트리거 빈도 - 추출 성공/실패율 - 추출된 메모리 타입 분포 - 추출된 메모리 중요도 분포
 */
@Component
public class MemoryExtractionMetricsConfiguration {

	private final MeterRegistry meterRegistry;

	// Counters
	private final Counter extractionTriggeredCounter;
	private final Counter extractionSuccessCounter;
	private final Counter extractionFailureCounter;

	// Distribution Summary
	private final DistributionSummary extractedImportanceScore;

	public MemoryExtractionMetricsConfiguration(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;

		// Extraction triggered
		this.extractionTriggeredCounter = Counter.builder("memory.extraction.triggered")
			.description("Number of times memory extraction was triggered")
			.register(meterRegistry);

		// Extraction success
		this.extractionSuccessCounter = Counter.builder("memory.extraction.success")
			.description("Number of successful memory extractions")
			.register(meterRegistry);

		// Extraction failure
		this.extractionFailureCounter = Counter.builder("memory.extraction.failure")
			.description("Number of failed memory extractions")
			.register(meterRegistry);

		// Extracted memory importance distribution
		this.extractedImportanceScore = DistributionSummary.builder("memory.extracted.importance")
			.description("Importance score for extracted memories")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);
	}

	/**
	 * 메모리 추출이 트리거되었음을 기록합니다.
	 */
	public void recordExtractionTriggered() {
		extractionTriggeredCounter.increment();
	}

	/**
	 * 메모리 추출 성공을 기록합니다.
	 */
	public void recordExtractionSuccess(int count) {
		extractionSuccessCounter.increment(count);
	}

	/**
	 * 메모리 추출 실패를 기록합니다.
	 */
	public void recordExtractionFailure() {
		extractionFailureCounter.increment();
	}

	/**
	 * 추출된 메모리 타입별 개수를 기록합니다.
	 */
	public void recordExtractedMemoryType(String type, int count) {
		Counter.builder("memory.extracted.count")
			.tag("type", type.toLowerCase())
			.description("Number of extracted memories by type")
			.register(meterRegistry)
			.increment(count);
	}

	/**
	 * 추출된 메모리 중요도를 기록합니다.
	 */
	public void recordExtractedImportance(double importance) {
		extractedImportanceScore.record(importance);
	}
}
