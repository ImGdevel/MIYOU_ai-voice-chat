package com.study.webflux.rag.domain.port.in;

import java.time.Instant;

import com.study.webflux.rag.domain.model.metrics.MetricsGranularity;
import com.study.webflux.rag.domain.model.metrics.MetricsRollup;
import com.study.webflux.rag.domain.model.metrics.PerformanceMetrics;
import com.study.webflux.rag.domain.model.metrics.PipelineDetail;
import com.study.webflux.rag.domain.model.metrics.StagePerformanceSummary;
import com.study.webflux.rag.domain.model.metrics.UsageAnalytics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MetricsQueryUseCase {

	Flux<PerformanceMetrics> getPerformanceMetricsByTimeRange(Instant startTime, Instant endTime);

	Flux<PerformanceMetrics> getRecentPerformanceMetrics(int limit);

	Flux<UsageAnalytics> getUsageAnalyticsByTimeRange(Instant startTime, Instant endTime);

	Flux<UsageAnalytics> getRecentUsageAnalytics(int limit);

	Mono<PipelineDetail> getPipelineDetail(String pipelineId);

	Flux<MetricsRollup> getMetricsRollups(MetricsGranularity granularity, int limit);

	Flux<StagePerformanceSummary> getStagePerformanceSummary(MetricsGranularity granularity,
		int limit);

	Mono<Long> getTotalRequestCount(Instant startTime, Instant endTime);

	Mono<Long> getTotalTokenUsage(Instant startTime, Instant endTime);
}
