package com.miyou.app.infrastructure.monitoring.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class UxMetricsConfiguration(
    private val meterRegistry: MeterRegistry,
) {
    private val firstResponseLatency =
        DistributionSummary
            .builder("ux.response.latency.first")
            .description("Time to first response token (TTFB) in milliseconds")
            .baseUnit("ms")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    private val completeResponseLatency =
        DistributionSummary
            .builder("ux.response.latency.complete")
            .description("Complete response time in milliseconds")
            .baseUnit("ms")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    private val errorRate =
        Counter
            .builder("ux.error.rate")
            .description("User-facing error count")
            .register(meterRegistry)

    private val satisfactionScore = AtomicReference(1.0)
    private val abandonmentRate =
        Counter
            .builder("ux.abandonment.rate")
            .description("Conversation abandonment count")
            .register(meterRegistry)

    private var totalRequests = 0L
    private var satisfiedRequests = 0L
    private var toleratingRequests = 0L

    init {
        Gauge
            .builder("ux.satisfaction.score", satisfactionScore, AtomicReference<Double>::get)
            .description("User satisfaction score (Apdex)")
            .register(meterRegistry)
    }

    fun recordFirstResponseLatency(latencyMs: Long) {
        firstResponseLatency.record(latencyMs.toDouble())
        updateSatisfactionScore(latencyMs)
    }

    fun recordCompleteResponseLatency(latencyMs: Long) {
        completeResponseLatency.record(latencyMs.toDouble())
    }

    fun recordError(errorType: String) {
        errorRate.increment()
        Counter
            .builder("ux.error.by_type")
            .description("User-facing error count by type")
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment()
    }

    fun recordAbandonment(stage: String) {
        abandonmentRate.increment()
        Counter
            .builder("ux.abandonment.by_stage")
            .description("Conversation abandonment count by stage")
            .tag("stage", stage)
            .register(meterRegistry)
            .increment()
    }

    private fun updateSatisfactionScore(latencyMs: Long) {
        totalRequests++
        when {
            latencyMs <= 2000L -> satisfiedRequests++
            latencyMs <= 8000L -> toleratingRequests++
        }

        val apdex = (satisfiedRequests + toleratingRequests * 0.5) / totalRequests.toDouble()
        satisfactionScore.set(apdex)
    }

    fun resetApdexCounters() {
        totalRequests = 0
        satisfiedRequests = 0
        toleratingRequests = 0
        satisfactionScore.set(1.0)
    }

    fun getApdexScore(): Double = satisfactionScore.get()

    fun getApdexGrade(): String {
        val score = satisfactionScore.get()
        return when {
            score >= 0.95 -> "Excellent"
            score >= 0.85 -> "Good"
            score >= 0.70 -> "Fair"
            score >= 0.50 -> "Poor"
            else -> "Unacceptable"
        }
    }
}
