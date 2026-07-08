package com.miyou.app.api.monitoring

import com.miyou.app.application.monitoring.model.UsageAnalyticsReport
import com.miyou.app.domain.cost.service.CostCalculationService
import com.miyou.app.monitoring.port.MetricsQueryUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/metrics")
class CostAnalyticsController(
    private val metricsQueryUseCase: MetricsQueryUseCase,
) {
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
            .map(UsageAnalyticsReport::toUsageMetricsInput)
            .map(CostCalculationService::calculateCost)
            .map { cost -> cost.totalCredits }
            .reduce(0L) { total, cost -> total + cost }

    data class TotalUsageSummary(
        val totalRequests: Long,
        val totalTokens: Long,
        val avgResponseTimeMillis: Double,
        val totalCredits: Long,
    )

    private companion object {
        const val MAX_CREDIT_SAMPLE = 10_000
    }
}
