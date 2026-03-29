package com.miyou.app.domain.monitoring.port;

import java.time.Instant;

import com.miyou.app.domain.monitoring.model.MetricsGranularity;
import com.miyou.app.domain.monitoring.model.MetricsRollup;
import com.miyou.app.domain.monitoring.model.PerformanceMetrics;
import com.miyou.app.domain.monitoring.model.PipelineDetail;
import com.miyou.app.domain.monitoring.model.StagePerformanceSummary;
import com.miyou.app.domain.monitoring.model.UsageAnalytics;
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

	Mono<Long> getTotalRequestCount();

	Mono<Long> getTotalTokenUsage();

	Mono<Double> getAverageResponseTime();
}
