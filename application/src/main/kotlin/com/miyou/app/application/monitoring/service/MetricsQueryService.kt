package com.miyou.app.application.monitoring.service

import com.miyou.app.domain.monitoring.model.MetricsGranularity
import com.miyou.app.domain.monitoring.model.MetricsRollup
import com.miyou.app.domain.monitoring.model.PerformanceMetrics
import com.miyou.app.domain.monitoring.model.PipelineDetail
import com.miyou.app.domain.monitoring.model.StagePerformanceRollup
import com.miyou.app.domain.monitoring.model.StagePerformanceSummary
import com.miyou.app.domain.monitoring.model.UsageAnalytics
import com.miyou.app.domain.monitoring.port.MetricsQueryUseCase
import com.miyou.app.domain.monitoring.port.MetricsRollupRepository
import com.miyou.app.domain.monitoring.port.PerformanceMetricsRepository
import com.miyou.app.domain.monitoring.port.StagePerformanceRollupRepository
import com.miyou.app.domain.monitoring.port.UsageAnalyticsRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

@Service
class MetricsQueryService(
    private val performanceMetricsRepository: PerformanceMetricsRepository,
    private val usageAnalyticsRepository: UsageAnalyticsRepository,
    private val metricsRollupRepository: MetricsRollupRepository,
    private val stagePerformanceRollupRepository: StagePerformanceRollupRepository,
) : MetricsQueryUseCase {
    override fun getPerformanceMetricsByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Flux<PerformanceMetrics> = performanceMetricsRepository.findByTimeRange(startTime, endTime)

    override fun getRecentPerformanceMetrics(limit: Int): Flux<PerformanceMetrics> =
        performanceMetricsRepository.findRecent(limit)

    override fun getUsageAnalyticsByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Flux<UsageAnalytics> = usageAnalyticsRepository.findByTimeRange(startTime, endTime)

    override fun getRecentUsageAnalytics(limit: Int): Flux<UsageAnalytics> = usageAnalyticsRepository.findRecent(limit)

    override fun getTotalRequestCount(
        startTime: Instant,
        endTime: Instant,
    ): Mono<Long> = usageAnalyticsRepository.countByTimeRange(startTime, endTime)

    override fun getTotalTokenUsage(
        startTime: Instant,
        endTime: Instant,
    ): Mono<Long> = usageAnalyticsRepository.sumTokensByTimeRange(startTime, endTime)

    override fun getTotalRequestCount(): Mono<Long> = usageAnalyticsRepository.count()

    override fun getTotalTokenUsage(): Mono<Long> = usageAnalyticsRepository.sumTokens()

    override fun getAverageResponseTime(): Mono<Double> = usageAnalyticsRepository.averageResponseTime()

    override fun getPipelineDetail(pipelineId: String): Mono<PipelineDetail> {
        val performanceMono =
            performanceMetricsRepository
                .findById(pipelineId)
                .map { Optional.of(it) }
                .defaultIfEmpty(Optional.empty())
        val usageMono =
            usageAnalyticsRepository
                .findById(pipelineId)
                .map { Optional.of(it) }
                .defaultIfEmpty(Optional.empty())

        return Mono
            .zip(performanceMono, usageMono)
            .filter { tuple -> tuple.t1.isPresent || tuple.t2.isPresent }
            .map { tuple -> PipelineDetail(pipelineId, tuple.t1.orElse(null), tuple.t2.orElse(null)) }
    }

    override fun getMetricsRollups(
        granularity: MetricsGranularity,
        limit: Int,
    ): Flux<MetricsRollup> {
        val endTime = Instant.now()
        val startTime = endTime.minus(windowSize(granularity, limit))
        if (granularity == MetricsGranularity.MINUTE) {
            return metricsRollupRepository.findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
                granularity,
                startTime,
                endTime,
            )
        }

        return metricsRollupRepository
            .findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
                MetricsGranularity.MINUTE,
                startTime,
                endTime,
            ).groupBy { rollup -> bucketStart(rollup.bucketStart, granularity) }
            .flatMap { group ->
                group.reduce(RollupAggregate(group.key(), granularity), RollupAggregate::add)
            }.map(RollupAggregate::toRollup)
            .sort { a, b -> a.bucketStart.compareTo(b.bucketStart) }
    }

    override fun getStagePerformanceSummary(
        granularity: MetricsGranularity,
        limit: Int,
    ): Flux<StagePerformanceSummary> {
        val endTime = Instant.now()
        val startTime = endTime.minus(windowSize(granularity, limit))

        return stagePerformanceRollupRepository
            .findByGranularityAndBucketStartBetween(
                MetricsGranularity.MINUTE,
                startTime,
                endTime,
            ).groupBy { it.stageName }
            .flatMap { group ->
                group.reduce(StageAggregate(group.key()), StageAggregate::add)
            }.map(StageAggregate::toSummary)
    }

    private fun windowSize(
        granularity: MetricsGranularity,
        limit: Int,
    ): Duration =
        when (granularity) {
            MetricsGranularity.DAY -> Duration.ofDays(limit.toLong())
            MetricsGranularity.HOUR -> Duration.ofHours(limit.toLong())
            MetricsGranularity.MINUTE -> Duration.ofMinutes(limit.toLong())
        }

    private fun bucketStart(
        instant: Instant,
        granularity: MetricsGranularity,
    ): Instant =
        when (granularity) {
            MetricsGranularity.DAY -> instant.truncatedTo(ChronoUnit.DAYS)
            MetricsGranularity.HOUR -> instant.truncatedTo(ChronoUnit.HOURS)
            MetricsGranularity.MINUTE -> instant.truncatedTo(ChronoUnit.MINUTES)
        }

    private class RollupAggregate(
        private val bucketStart: Instant,
        private val granularity: MetricsGranularity,
    ) {
        private var requestCount: Long = 0
        private var totalTokens: Long = 0
        private var totalDurationMillis: Long = 0

        fun add(rollup: MetricsRollup): RollupAggregate {
            requestCount += rollup.requestCount
            totalTokens += rollup.totalTokens
            totalDurationMillis += rollup.totalDurationMillis
            return this
        }

        fun toRollup(): MetricsRollup {
            val avg = if (requestCount == 0L) 0.0 else totalDurationMillis.toDouble() / requestCount
            return MetricsRollup(
                bucketStart,
                granularity,
                requestCount,
                totalTokens,
                totalDurationMillis,
                avg,
            )
        }
    }

    private class StageAggregate(
        private val stageName: String,
    ) {
        private var count: Long = 0
        private var totalDurationMillis: Long = 0

        fun add(rollup: StagePerformanceRollup): StageAggregate {
            count += rollup.count
            totalDurationMillis += rollup.totalDurationMillis
            return this
        }

        fun toSummary(): StagePerformanceSummary {
            val avg = if (count == 0L) 0.0 else totalDurationMillis.toDouble() / count
            return StagePerformanceSummary(stageName, avg)
        }
    }
}
