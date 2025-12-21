package com.study.webflux.rag.domain.port.out;

import java.time.Instant;

import com.study.webflux.rag.domain.model.metrics.PerformanceMetrics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PerformanceMetricsRepository {
	Mono<PerformanceMetrics> save(PerformanceMetrics metrics);

	Mono<PerformanceMetrics> findById(String pipelineId);

	Flux<PerformanceMetrics> findByTimeRange(Instant startTime, Instant endTime);

	Flux<PerformanceMetrics> findByStatus(String status, int limit);

	Flux<PerformanceMetrics> findSlowPipelines(long thresholdMillis, int limit);

	Flux<PerformanceMetrics> findRecent(int limit);
}
