package com.miyou.app.domain.monitoring.port

import com.miyou.app.domain.monitoring.model.MetricsGranularity
import com.miyou.app.domain.monitoring.model.MetricsRollup
import com.miyou.app.domain.monitoring.model.PerformanceMetrics
import com.miyou.app.domain.monitoring.model.PipelineDetail
import com.miyou.app.domain.monitoring.model.StagePerformanceSummary
import com.miyou.app.domain.monitoring.model.UsageAnalytics
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface MetricsQueryUseCase {
    fun getPerformanceMetricsByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Flux<PerformanceMetrics>

    fun getRecentPerformanceMetrics(limit: Int): Flux<PerformanceMetrics>

    fun getUsageAnalyticsByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Flux<UsageAnalytics>

    fun getRecentUsageAnalytics(limit: Int): Flux<UsageAnalytics>

    fun getPipelineDetail(pipelineId: String): Mono<PipelineDetail>

    fun getMetricsRollups(
        granularity: MetricsGranularity,
        limit: Int,
    ): Flux<MetricsRollup>

    fun getStagePerformanceSummary(
        granularity: MetricsGranularity,
        limit: Int,
    ): Flux<StagePerformanceSummary>

    fun getTotalRequestCount(
        startTime: Instant,
        endTime: Instant,
    ): Mono<Long>

    fun getTotalTokenUsage(
        startTime: Instant,
        endTime: Instant,
    ): Mono<Long>

    fun getTotalRequestCount(): Mono<Long>

    fun getTotalTokenUsage(): Mono<Long>

    fun getAverageResponseTime(): Mono<Double>
}
