package com.miyou.app.domain.cost.model

/**
 * 모델별 비용 계산식 정의.
 */
class ModelPricing {
    companion object {
        private const val CREDITS_PER_DOLLAR = 10000.0

        private val llmPrices: Map<String, PricePerMillion> =
            mapOf(
                "gpt-5.2" to PricePerMillion(1.75, 14.00),
                "gpt-5.1" to PricePerMillion(1.25, 10.00),
                "gpt-5" to PricePerMillion(1.25, 10.00),
                "gpt-5-mini" to PricePerMillion(0.25, 2.00),
                "gpt-5-nano" to PricePerMillion(0.05, 0.40),
                "gpt-4.1" to PricePerMillion(2.00, 8.00),
                "gpt-4.1-mini" to PricePerMillion(0.40, 1.60),
                "gpt-4.1-nano" to PricePerMillion(0.10, 0.40),
                "gpt-4o-2024-05-13" to PricePerMillion(5.00, 15.00),
                "gpt-4o-mini" to PricePerMillion(0.150, 0.600),
                "gpt-4o" to PricePerMillion(2.50, 10.00),
                "gpt-4-turbo" to PricePerMillion(10.00, 30.00),
                "gpt-3.5-turbo" to PricePerMillion(0.50, 1.50)
            )

        private val embeddingPrices: Map<String, EmbeddingPricePerMillion> =
            mapOf(
                "text-embedding-3-small" to EmbeddingPricePerMillion(0.020, 0.010),
                "text-embedding-3-large" to EmbeddingPricePerMillion(0.130, 0.065),
                "text-embedding-ada-002" to EmbeddingPricePerMillion(0.100, 0.050)
            )

        private const val TTS_PRICE_PER_100MS = 0.00015

        @JvmStatic
        fun calculateLlmCredits(
            model: String,
            promptTokens: Int,
            completionTokens: Int,
        ): Long {
            val price = llmPrices[model] ?: PricePerMillion(0.150, 0.600)

            val inputCost = (promptTokens / 1_000_000.0) * price.inputPrice
            val outputCost = (completionTokens / 1_000_000.0) * price.outputPrice
            val totalDollars = inputCost + outputCost

            return kotlin.math.ceil(totalDollars * CREDITS_PER_DOLLAR).toLong()
        }

        @JvmStatic
        fun calculateTtsCredits(audioLengthMillis: Long): Long {
            val totalCost = (audioLengthMillis / 100.0) * TTS_PRICE_PER_100MS
            return kotlin.math.ceil(totalCost * CREDITS_PER_DOLLAR).toLong()
        }

        @JvmStatic
        fun calculateEmbeddingCredits(
            model: String,
            tokens: Long,
            batch: Boolean,
        ): Long {
            val price = embeddingPrices[model] ?: return 0L
            val perMillion = if (batch) price.batchPrice else price.cost
            val totalCost = (tokens / 1_000_000.0) * perMillion
            return kotlin.math.ceil(totalCost * CREDITS_PER_DOLLAR).toLong()
        }
    }

    private data class PricePerMillion(
        val inputPrice: Double,
        val outputPrice: Double,
    )

    private data class EmbeddingPricePerMillion(
        val cost: Double,
        val batchPrice: Double,
    )
}
