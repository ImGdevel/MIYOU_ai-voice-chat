package com.miyou.app.infrastructure.monitoring.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class LlmMetricsConfiguration(
    private val meterRegistry: MeterRegistry,
) {
    private val llmRequestCounter =
        Counter
            .builder("llm.request.count")
            .description("Total number of LLM requests")
            .register(meterRegistry)

    private val llmSuccessCounter =
        Counter
            .builder("llm.request.success")
            .description("Number of successful LLM requests")
            .register(meterRegistry)

    private val llmFailureCounter =
        Counter
            .builder("llm.request.failure")
            .description("Number of failed LLM requests")
            .register(meterRegistry)

    private val promptLengthSummary =
        DistributionSummary
            .builder("llm.prompt.length")
            .description("Distribution of prompt token lengths")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    private val completionLengthSummary =
        DistributionSummary
            .builder("llm.completion.length")
            .description("Distribution of completion token lengths")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    private val llmResponseTimeSummary =
        DistributionSummary
            .builder("llm.response.time.ms")
            .description("Distribution of LLM response times in milliseconds")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    fun recordLlmRequest() {
        llmRequestCounter.increment()
    }

    fun recordLlmSuccess(model: String) {
        llmSuccessCounter.increment()
        Counter
            .builder("llm.success.by_model")
            .tag("model", model)
            .description("Number of successful LLM requests by model")
            .register(meterRegistry)
            .increment()
    }

    fun recordLlmFailure(
        model: String,
        errorType: String,
    ) {
        llmFailureCounter.increment()
        Counter
            .builder("llm.failure.by_model")
            .tag("model", model)
            .tag("error_type", errorType)
            .description("Number of failed LLM requests by model and error type")
            .register(meterRegistry)
            .increment()
    }

    fun recordPromptLength(tokens: Int) {
        promptLengthSummary.record(tokens.toDouble())
    }

    fun recordCompletionLength(tokens: Int) {
        completionLengthSummary.record(tokens.toDouble())
    }

    fun recordResponseTime(milliseconds: Long) {
        llmResponseTimeSummary.record(milliseconds.toDouble())
    }

    fun recordResponseTimeByModel(
        model: String,
        milliseconds: Long,
    ) {
        DistributionSummary
            .builder("llm.response.time.by_model")
            .tag("model", model)
            .description("LLM response time distribution by model")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)
            .record(milliseconds.toDouble())
    }
}
