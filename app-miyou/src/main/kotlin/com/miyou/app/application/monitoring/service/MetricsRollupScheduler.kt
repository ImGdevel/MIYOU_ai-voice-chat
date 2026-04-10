package com.miyou.app.application.monitoring.service

import com.miyou.app.domain.monitoring.model.MetricsGranularity
import com.miyou.app.domain.monitoring.model.MetricsRollup
import com.miyou.app.domain.monitoring.model.PerformanceMetrics
import com.miyou.app.domain.monitoring.model.StagePerformanceRollup
import com.miyou.app.domain.monitoring.model.UsageAnalytics
import com.miyou.app.domain.monitoring.port.MetricsRollupRepository
import com.miyou.app.domain.monitoring.port.PerformanceMetricsRepository
import com.miyou.app.domain.monitoring.port.StagePerformanceRollupRepository
import com.miyou.app.domain.monitoring.port.UsageAnalyticsRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class MetricsRollupScheduler(
    private val usageAnalyticsRepository: UsageAnalyticsRepository,
    private val performanceMetricsRepository: PerformanceMetricsRepository,
    private val metricsRollupRepository: MetricsRollupRepository,
    private val stagePerformanceRollupRepository: StagePerformanceRollupRepository,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(MetricsRollupScheduler::class.java)

    @Scheduled(cron = "0 * * * * *")
    fun rollupMinuteMetrics() {
        val bucketStart = previousMinuteBucketStart()
        val bucketEnd = bucketStart.plus(1, ChronoUnit.MINUTES)

        Mono
            .zip(buildUsageRollup(bucketStart, bucketEnd), buildStageRollup(bucketStart, bucketEnd))
            .doOnSuccess {
                logger.debug("분단위 집계 완료: bucketStart={}", bucketStart)
            }.doOnError { error ->
                logger.error("분단위 집계 실패: bucketStart={}, error={}", bucketStart, error.message, error)
            }.onErrorResume { Mono.empty() }
            .subscribe()
    }

    private fun previousMinuteBucketStart(): Instant =
        clock.instant().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.MINUTES)

    private fun buildUsageRollup(
        bucketStart: Instant,
        bucketEnd: Instant,
    ): Mono<Void> =
        usageAnalyticsRepository
            .findByTimeRange(bucketStart, bucketEnd)
            .reduce(UsageAggregate(), UsageAggregate::add)
            .defaultIfEmpty(UsageAggregate())
            .map { aggregate -> toMetricsRollup(bucketStart, aggregate) }
            .flatMap(metricsRollupRepository::save)
            .then()

    private fun buildStageRollup(
        bucketStart: Instant,
        bucketEnd: Instant,
    ): Mono<Void> {
        val rollups: Flux<StagePerformanceRollup> =
            performanceMetricsRepository
                .findByTimeRange(bucketStart, bucketEnd)
                .flatMapIterable { metrics ->
                    metrics.stages ?: emptyList()
                }.groupBy { stage -> stage.stageName }
                .flatMap { group -> group.reduce(StageAggregate(group.key()), StageAggregate::add) }
                .map { aggregate -> aggregate.toRollup(bucketStart) }

        return stagePerformanceRollupRepository.saveAll(rollups).then()
    }

    private fun toMetricsRollup(
        bucketStart: Instant,
        aggregate: UsageAggregate,
    ): MetricsRollup {
        val avg =
            if (aggregate.requestCount == 0L) {
                0.0
            } else {
                aggregate.totalDurationMillis.toDouble() / aggregate.requestCount
            }
        return MetricsRollup(
            bucketStart,
            MetricsGranularity.MINUTE,
            aggregate.requestCount,
            aggregate.totalTokens,
            aggregate.totalDurationMillis,
            avg,
        )
    }

    private class UsageAggregate {
        var requestCount: Long = 0
        var totalTokens: Long = 0
        var totalDurationMillis: Long = 0

        fun add(analytics: UsageAnalytics): UsageAggregate {
            requestCount += 1
            analytics.llmUsage?.let { totalTokens += it.totalTokens }
            analytics.responseMetrics?.let { totalDurationMillis += it.totalDurationMillis }
            return this
        }
    }

    private class StageAggregate(
        private val stageName: String,
    ) {
        var count: Long = 0
        var totalDurationMillis: Long = 0

        fun add(stage: PerformanceMetrics.StagePerformance): StageAggregate {
            count += 1
            totalDurationMillis += stage.durationMillis
            return this
        }

        fun toRollup(bucketStart: Instant): StagePerformanceRollup {
            val avg = if (count == 0L) 0.0 else totalDurationMillis.toDouble() / count
            return StagePerformanceRollup(
                bucketStart,
                MetricsGranularity.MINUTE,
                stageName,
                count,
                totalDurationMillis,
                avg,
            )
        }
    }
}
