package com.miyou.app.infrastructure.inbound.web.monitoring

import com.miyou.app.domain.cost.service.CostCalculationService
import com.miyou.app.domain.monitoring.model.MetricsGranularity
import com.miyou.app.domain.monitoring.model.MetricsRollup
import com.miyou.app.domain.monitoring.model.PerformanceMetrics
import com.miyou.app.domain.monitoring.model.PipelineDetail
import com.miyou.app.domain.monitoring.model.StagePerformanceSummary
import com.miyou.app.domain.monitoring.model.UsageAnalytics
import com.miyou.app.domain.monitoring.port.MetricsQueryUseCase
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/metrics")
@Validated
class MetricsController(
    private val metricsQueryUseCase: MetricsQueryUseCase,
) {
    @GetMapping("/performance")
    fun getPerformanceMetrics(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: Instant?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: Instant?,
    ): Flux<PerformanceMetrics> {
        val resolvedStartTime = startTime ?: Instant.now().minus(24, ChronoUnit.HOURS)
        val resolvedEndTime = endTime ?: Instant.now()

        return metricsQueryUseCase.getPerformanceMetricsByTimeRange(
            resolvedStartTime,
            resolvedEndTime,
        )
    }

    @GetMapping("/performance/recent")
    fun getRecentPerformanceMetrics(
        @RequestParam(defaultValue = "20") @Min(1) limit: Int,
    ): Flux<PerformanceMetrics> = metricsQueryUseCase.getRecentPerformanceMetrics(limit)

    @GetMapping("/usage")
    fun getUsageAnalytics(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: Instant?,
    ): Flux<UsageAnalytics> {
        val resolvedStartTime = startTime ?: Instant.now().minus(24, ChronoUnit.HOURS)
        val resolvedEndTime = endTime ?: Instant.now()

        return metricsQueryUseCase.getUsageAnalyticsByTimeRange(
            resolvedStartTime,
            resolvedEndTime,
        )
    }

    @GetMapping("/usage/recent")
    fun getRecentUsageAnalytics(
        @RequestParam(defaultValue = "20") @Min(1) limit: Int,
    ): Flux<UsageAnalytics> = metricsQueryUseCase.getRecentUsageAnalytics(limit)

    @GetMapping("/usage/summary")
    fun getUsageSummary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: Instant?,
    ): Mono<UsageSummary> {
        val resolvedStartTime = startTime ?: Instant.now().minus(24, ChronoUnit.HOURS)
        val resolvedEndTime = endTime ?: Instant.now()

        return Mono
            .zip(
                metricsQueryUseCase.getTotalRequestCount(resolvedStartTime, resolvedEndTime),
                metricsQueryUseCase.getTotalTokenUsage(resolvedStartTime, resolvedEndTime),
            ).map { tuple ->
                UsageSummary(
                    resolvedStartTime,
                    resolvedEndTime,
                    tuple.t1,
                    tuple.t2,
                )
            }
    }

    @GetMapping("/usage/summary/total")
    fun getTotalUsageSummary(): Mono<TotalUsageSummary> =
        Mono
            .zip(
                metricsQueryUseCase.getTotalRequestCount(),
                metricsQueryUseCase.getTotalTokenUsage(),
                metricsQueryUseCase.getAverageResponseTime(),
                calculateTotalCredits(),
            ).map { tuple ->
                TotalUsageSummary(
                    tuple.t1,
                    tuple.t2,
                    tuple.t3,
                    tuple.t4,
                )
            }

    private fun calculateTotalCredits(): Mono<Long> =
        metricsQueryUseCase
            .getRecentUsageAnalytics(MAX_CREDIT_SAMPLE)
            .map(CostCalculationService::calculateCost)
            .map { cost -> cost.totalCredits }
            .reduce(0L) { total, cost -> total + cost }

    @GetMapping("/pipeline/{pipelineId}")
    fun getPipelineDetail(
        @PathVariable pipelineId: String,
    ): Mono<PipelineDetailResponse> =
        metricsQueryUseCase
            .getPipelineDetail(pipelineId)
            .map(PipelineDetailResponse::fromDomain)
            .switchIfEmpty(
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pipeline not found: $pipelineId",
                    ),
                ),
            )

    @GetMapping("/rollups")
    fun getMetricsRollups(
        @RequestParam(defaultValue = "MINUTE") granularity: MetricsGranularity,
        @RequestParam(defaultValue = "60") @Min(1) limit: Int,
    ): Flux<MetricsRollup> = metricsQueryUseCase.getMetricsRollups(granularity, limit)

    @GetMapping("/stages/summary")
    fun getStagePerformanceSummary(
        @RequestParam(defaultValue = "MINUTE") granularity: MetricsGranularity,
        @RequestParam(defaultValue = "60") @Min(1) limit: Int,
    ): Flux<StagePerformanceSummary> = metricsQueryUseCase.getStagePerformanceSummary(granularity, limit)

    data class UsageSummary(
        val startTime: Instant,
        val endTime: Instant,
        val totalRequests: Long,
        val totalTokens: Long,
    )

    data class TotalUsageSummary(
        val totalRequests: Long,
        val totalTokens: Long,
        val avgResponseTimeMillis: Double,
        val totalCredits: Long,
    )

    data class PipelineDetailResponse(
        val pipelineId: String,
        val performance: PerformanceMetrics,
        val usage: UsageAnalytics,
    ) {
        companion object {
            fun fromDomain(detail: PipelineDetail): PipelineDetailResponse =
                PipelineDetailResponse(
                    detail.pipelineId,
                    detail.performance,
                    detail.usage,
                )
        }
    }

    companion object {
        private const val MAX_CREDIT_SAMPLE = 10_000
    }
}
