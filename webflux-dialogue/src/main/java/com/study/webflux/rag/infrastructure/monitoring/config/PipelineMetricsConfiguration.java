package com.study.webflux.rag.infrastructure.monitoring.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 파이프라인 병목 분석을 위한 메트릭을 제공합니다.
 *
 * <p>
 * Phase 1A: Pipeline Bottleneck Analysis - Stage 간 Gap 측정 (전환 시간) - Backpressure 지표 (Queue, Buffer) - Stage별 데이터 크기
 */
@Configuration
public class PipelineMetricsConfiguration {

	/**
	 * Backpressure 메트릭을 추적하는 컴포넌트입니다.
	 */
	@Component
	public static class BackpressureMetrics {

		private final MeterRegistry meterRegistry;

		// Sentence buffer size
		private final AtomicInteger sentenceBufferSize = new AtomicInteger(0);

		// Stage별 데이터 크기 (bytes)
		private final ConcurrentHashMap<String, AtomicInteger> stageDataSizes = new ConcurrentHashMap<>();

		public BackpressureMetrics(MeterRegistry meterRegistry) {
			this.meterRegistry = meterRegistry;
			registerMetrics();
		}

		private void registerMetrics() {
			// Sentence buffer size gauge
			Gauge.builder("pipeline.sentence.buffer.size", sentenceBufferSize, AtomicInteger::get)
				.description("Current size of sentence buffer")
				.register(meterRegistry);
		}

		/**
		 * Sentence buffer 크기를 업데이트합니다.
		 */
		public void updateSentenceBufferSize(int size) {
			sentenceBufferSize.set(size);
		}

		/**
		 * Stage별 데이터 크기를 기록합니다.
		 */
		public void recordStageDataSize(String stage, String dataType, int sizeBytes) {
			String key = stage + ":" + dataType;
			stageDataSizes.computeIfAbsent(key, k -> {
				AtomicInteger gauge = new AtomicInteger(0);
				Gauge.builder("pipeline.data.size.bytes", gauge, AtomicInteger::get)
					.tag("stage", stage)
					.tag("data_type", dataType)
					.description("Data size in bytes for pipeline stage")
					.register(meterRegistry);
				return gauge;
			}).set(sizeBytes);
		}

		/**
		 * 현재 Sentence buffer 크기를 반환합니다.
		 */
		public int getSentenceBufferSize() {
			return sentenceBufferSize.get();
		}
	}
}
