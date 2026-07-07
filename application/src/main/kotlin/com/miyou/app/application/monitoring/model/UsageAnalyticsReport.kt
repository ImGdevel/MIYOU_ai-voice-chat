package com.miyou.app.application.monitoring.model

import com.miyou.app.domain.cost.model.CostInfo
import com.miyou.app.domain.cost.model.UsageMetricsInput
import com.miyou.app.domain.cost.service.CostCalculationService
import com.miyou.app.domain.monitoring.model.UsageAnalytics

/**
 * `monitoring` 도메인의 [UsageAnalytics]와 `cost` 도메인의 [CostInfo]를 조합하는
 * application 계층 타입. 두 도메인이 서로를 직접 참조하지 않도록 조합 책임을 이곳으로 이관한다.
 */
data class UsageAnalyticsReport(
    val analytics: UsageAnalytics,
    val cost: CostInfo,
) {
    companion object {
        @JvmStatic
        fun of(analytics: UsageAnalytics): UsageAnalyticsReport {
            val input = toUsageMetricsInput(analytics)
            return UsageAnalyticsReport(analytics, CostCalculationService.calculateCost(input))
        }

        @JvmStatic
        fun toUsageMetricsInput(analytics: UsageAnalytics?): UsageMetricsInput? {
            if (analytics == null) {
                return null
            }

            val llmUsage = analytics.llmUsage
            val userRequest = analytics.userRequest
            val retrievalMetrics = analytics.retrievalMetrics
            val ttsMetrics = analytics.ttsMetrics

            return UsageMetricsInput(
                model = llmUsage?.model,
                promptTokens = llmUsage?.promptTokens,
                completionTokens = llmUsage?.completionTokens,
                totalTokens = llmUsage?.totalTokens ?: 0,
                inputLength = userRequest?.inputLength ?: 0,
                memoryCount = retrievalMetrics?.memoryCount ?: 0,
                documentCount = retrievalMetrics?.documentCount ?: 0,
                sentenceCount = ttsMetrics?.sentenceCount ?: 0,
            )
        }
    }
}
