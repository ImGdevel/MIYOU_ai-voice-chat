package com.miyou.app.domain.cost.service

import com.miyou.app.domain.cost.model.CostInfo
import com.miyou.app.domain.cost.model.ModelPricing
import com.miyou.app.domain.cost.model.UsageMetricsInput

class CostCalculationService {
    companion object {
        @JvmStatic
        fun calculateCost(input: UsageMetricsInput?): CostInfo {
            if (input == null) {
                return CostInfo.zero()
            }

            val llmCredits = calculateLlmCredits(input)
            val ttsCredits = calculateTtsCredits(input)

            return CostInfo.of(llmCredits, ttsCredits)
        }

        private fun calculateLlmCredits(input: UsageMetricsInput): Long {
            val model = input.model.orEmpty()

            val promptTokens =
                input.promptTokens.takeIf { it != null }
                    ?: estimatePromptTokens(input)
            val completionTokens = input.completionTokens ?: input.totalTokens

            return ModelPricing.calculateLlmCredits(model, promptTokens, completionTokens)
        }

        private fun estimatePromptTokens(input: UsageMetricsInput): Int {
            val inputLength = input.inputLength
            val memoryCount = input.memoryCount
            val documentCount = input.documentCount

            val basePromptTokens = 300
            val inputTokens = inputLength / 3
            val contextTokens = (memoryCount * 50) + (documentCount * 100)

            return basePromptTokens + inputTokens + contextTokens
        }

        private fun calculateTtsCredits(input: UsageMetricsInput): Long {
            val sentenceCount = input.sentenceCount
            val estimatedAudioLength = estimateAudioLength(sentenceCount)

            return ModelPricing.calculateTtsCredits(estimatedAudioLength)
        }

        private fun estimateAudioLength(sentenceCount: Int): Long {
            val avgSentenceDuration = 3000L
            return sentenceCount.toLong() * avgSentenceDuration
        }
    }
}
