package com.miyou.app.api.monitoring

import com.miyou.app.monitoring.model.UsageAnalytics
import com.miyou.app.monitoring.port.MetricsQueryUseCase
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
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
class UsageAnalyticsController(
    private val metricsQueryUseCase: MetricsQueryUseCase,
) {
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

    data class UsageSummary(
        val startTime: Instant,
        val endTime: Instant,
        val totalRequests: Long,
        val totalTokens: Long,
    )
}
