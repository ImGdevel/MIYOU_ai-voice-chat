package com.miyou.app.domain.cost.service

import com.miyou.app.domain.cost.model.CostInfo
import com.miyou.app.domain.cost.model.ModelPricing
import com.miyou.app.domain.monitoring.model.UsageAnalytics

class CostCalculationService {
    companion object {
        @JvmStatic
        fun calculateCost(analytics: UsageAnalytics?): CostInfo {
            if (analytics == null) {
                return CostInfo.zero()
            }

            val llmCredits = calculateLlmCredits(analytics)
            val ttsCredits = calculateTtsCredits(analytics)

            return CostInfo.of(llmCredits, ttsCredits)
        }

        private fun calculateLlmCredits(analytics: UsageAnalytics): Long {
            val llmUsage = analytics.llmUsage ?: return 0L
            val model = llmUsage.model

            val promptTokens =
                llmUsage.promptTokens.takeIf { it != null }
                    ?: estimatePromptTokens(analytics)
            val completionTokens = llmUsage.completionTokens ?: llmUsage.totalTokens

            return ModelPricing.calculateLlmCredits(model, promptTokens, completionTokens)
        }

        private fun estimatePromptTokens(analytics: UsageAnalytics): Int {
            val userRequest = analytics.userRequest ?: return 0

            val inputLength = userRequest.inputLength
            val retrievalMetrics = analytics.retrievalMetrics
            val memoryCount = retrievalMetrics?.memoryCount ?: 0
            val documentCount = retrievalMetrics?.documentCount ?: 0

            val basePromptTokens = 300
            val inputTokens = inputLength / 3
            val contextTokens = (memoryCount * 50) + (documentCount * 100)

            return basePromptTokens + inputTokens + contextTokens
        }

        private fun calculateTtsCredits(analytics: UsageAnalytics): Long {
            val ttsMetrics = analytics.ttsMetrics ?: return 0L
            val sentenceCount = ttsMetrics.sentenceCount
            val estimatedAudioLength = estimateAudioLength(sentenceCount)

            return ModelPricing.calculateTtsCredits(estimatedAudioLength)
        }

        private fun estimateAudioLength(sentenceCount: Int): Long {
            val avgSentenceDuration = 3000L
            return sentenceCount.toLong() * avgSentenceDuration
        }
    }
}
