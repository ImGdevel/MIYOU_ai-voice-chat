package com.miyou.app.infrastructure.monitoring.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

@Component
class CostTrackingMetricsConfiguration(
    private val meterRegistry: MeterRegistry,
) {
    // LLM 비용
    private val llmCostTotal =
        Counter
            .builder("llm.cost.usd.total")
            .description("Total LLM cost in USD")
            .baseUnit("usd")
            .register(meterRegistry)

    private val llmCostDaily = AtomicReference(0.0)
    private val llmCostMonthly = AtomicReference(0.0)

    // TTS 비용
    private val ttsCostTotal =
        Counter
            .builder("tts.cost.usd.total")
            .description("Total TTS cost in USD")
            .baseUnit("usd")
            .register(meterRegistry)

    private val ttsCostDaily = AtomicReference(0.0)
    private val ttsCostMonthly = AtomicReference(0.0)

    // 예산 잔여
    private val budgetRemaining = AtomicReference(1000.0)

    init {
        Gauge
            .builder("llm.cost.usd.daily", llmCostDaily, AtomicReference<Double>::get)
            .description("Daily LLM cost in USD")
            .baseUnit("usd")
            .register(meterRegistry)

        Gauge
            .builder("llm.cost.usd.monthly", llmCostMonthly, AtomicReference<Double>::get)
            .description("Monthly LLM cost in USD")
            .baseUnit("usd")
            .register(meterRegistry)

        Gauge
            .builder("tts.cost.usd.daily", ttsCostDaily, AtomicReference<Double>::get)
            .description("Daily TTS cost in USD")
            .baseUnit("usd")
            .register(meterRegistry)

        Gauge
            .builder("tts.cost.usd.monthly", ttsCostMonthly, AtomicReference<Double>::get)
            .description("Monthly TTS cost in USD")
            .baseUnit("usd")
            .register(meterRegistry)

        Gauge
            .builder("cost.budget.remaining", budgetRemaining, AtomicReference<Double>::get)
            .description("Remaining budget in USD")
            .baseUnit("usd")
            .tag("budget_type", "monthly")
            .register(meterRegistry)
    }

    fun recordLlmCost(
        model: String,
        promptTokens: Int,
        completionTokens: Int,
    ) {
        val cost = calculateLlmCost(model, promptTokens, completionTokens)
        llmCostTotal.increment(cost)

        Counter
            .builder("llm.cost.by_model")
            .description("LLM cost by model")
            .baseUnit("usd")
            .tag("model", model)
            .register(meterRegistry)
            .increment(cost)

        llmCostDaily.updateAndGet { current -> current + cost }
        llmCostMonthly.updateAndGet { current -> current + cost }
        budgetRemaining.updateAndGet { current -> max(0.0, current - cost) }
    }

    fun recordUserLlmCost(
        userId: String,
        model: String,
        cost: Double,
    ) {
        Counter
            .builder("llm.cost.by_user")
            .description("LLM cost by user")
            .baseUnit("usd")
            .tag("user_id", userId)
            .tag("model", model)
            .register(meterRegistry)
            .increment(cost)
    }

    fun recordTtsCost(
        provider: String,
        characters: Int,
    ) {
        val cost = calculateTtsCost(provider, characters)
        ttsCostTotal.increment(cost)

        Counter
            .builder("tts.cost.by_provider")
            .description("TTS cost by provider")
            .baseUnit("usd")
            .tag("provider", provider)
            .register(meterRegistry)
            .increment(cost)

        ttsCostDaily.updateAndGet { current -> current + cost }
        ttsCostMonthly.updateAndGet { current -> current + cost }
        budgetRemaining.updateAndGet { current -> max(0.0, current - cost) }
    }

    fun resetDailyCost() {
        llmCostDaily.set(0.0)
        ttsCostDaily.set(0.0)
    }

    fun resetMonthlyCost() {
        llmCostMonthly.set(0.0)
        ttsCostMonthly.set(0.0)
        budgetRemaining.set(1000.0)
    }

    fun updateBudget(budget: Double) {
        budgetRemaining.set(budget)
    }

    private fun calculateLlmCost(
        model: String,
        promptTokens: Int,
        completionTokens: Int,
    ): Double {
        val modelLower = model.lowercase()
        return when {
            modelLower.contains("gpt-4o") && !modelLower.contains("mini") -> {
                (promptTokens / 1_000_000.0) * 2.50 + (completionTokens / 1_000_000.0) * 10.00
            }

            modelLower.contains("gpt-4o-mini") -> {
                (promptTokens / 1_000_000.0) * 0.150 + (completionTokens / 1_000_000.0) * 0.600
            }

            modelLower.contains("gpt-4-turbo") -> {
                (promptTokens / 1_000_000.0) * 10.00 + (completionTokens / 1_000_000.0) * 30.00
            }

            modelLower.contains("gpt-3.5-turbo") -> {
                (promptTokens / 1_000_000.0) * 0.50 + (completionTokens / 1_000_000.0) * 1.50
            }

            else -> {
                ((promptTokens + completionTokens) / 1_000_000.0) * 5.0
            }
        }
    }

    private fun calculateTtsCost(
        provider: String,
        characters: Int,
    ): Double {
        val providerLower = provider.lowercase()
        return when {
            providerLower.contains("supertone") -> {
                (characters / 1_000.0) * 0.015
            }

            providerLower.contains("openai") || providerLower.contains("tts-1") -> {
                (characters / 1_000.0) * 0.015
            }

            else -> {
                (characters / 1_000.0) * 0.01
            }
        }
    }
}
