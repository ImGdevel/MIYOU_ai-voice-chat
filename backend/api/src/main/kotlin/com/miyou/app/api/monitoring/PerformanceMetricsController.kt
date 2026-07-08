package com.miyou.app.api.monitoring

import com.miyou.app.exception.CommonErrorCode
import com.miyou.app.monitoring.exception.PipelineNotFoundException
import com.miyou.app.monitoring.model.MetricsGranularity
import com.miyou.app.monitoring.model.MetricsRollup
import com.miyou.app.monitoring.model.PerformanceMetrics
import com.miyou.app.monitoring.model.PipelineDetail
import com.miyou.app.monitoring.model.StagePerformanceSummary
import com.miyou.app.monitoring.model.UsageAnalytics
import com.miyou.app.monitoring.port.MetricsQueryUseCase
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/metrics")
@Validated
class PerformanceMetricsController(
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

    @GetMapping("/pipeline/{pipelineId}")
    fun getPipelineDetail(
        @PathVariable pipelineId: String,
    ): Mono<PipelineDetailResponse> =
        metricsQueryUseCase
            .getPipelineDetail(pipelineId)
            .map(PipelineDetailResponse::fromDomain)
            .switchIfEmpty(
                Mono.error(PipelineNotFoundException(pipelineId))
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
}
