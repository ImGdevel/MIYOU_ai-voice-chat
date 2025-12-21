package com.study.webflux.rag.domain.port.in;

import java.time.Instant;

import com.study.webflux.rag.domain.model.metrics.PerformanceMetrics;
import com.study.webflux.rag.domain.model.metrics.UsageAnalytics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MetricsQueryUseCase {

	Flux<PerformanceMetrics> getPerformanceMetricsByTimeRange(Instant startTime, Instant endTime);

	Flux<PerformanceMetrics> getSlowPipelines(long thresholdMillis, int limit);

	Flux<PerformanceMetrics> getRecentPerformanceMetrics(int limit);

	Flux<UsageAnalytics> getUsageAnalyticsByTimeRange(Instant startTime, Instant endTime);

	Flux<UsageAnalytics> getHighTokenUsage(int tokenThreshold, int limit);

	Flux<UsageAnalytics> getRecentUsageAnalytics(int limit);

	Mono<Long> getTotalRequestCount(Instant startTime, Instant endTime);

	Mono<Long> getTotalTokenUsage(Instant startTime, Instant endTime);
}
