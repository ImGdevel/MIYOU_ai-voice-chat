package com.miyou.app.infrastructure.monitoring.config

import com.miyou.app.application.monitoring.port.MemoryExtractionMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class MemoryExtractionMetricsConfiguration(
    private val meterRegistry: MeterRegistry,
) : MemoryExtractionMetricsPort {
    private val extractionTriggeredCounter =
        Counter
            .builder("memory.extraction.triggered")
            .description("Number of times memory extraction was triggered")
            .register(meterRegistry)

    private val extractionSuccessCounter =
        Counter
            .builder("memory.extraction.success")
            .description("Number of successful memory extractions")
            .register(meterRegistry)

    private val extractionFailureCounter =
        Counter
            .builder("memory.extraction.failure")
            .description("Number of failed memory extractions")
            .register(meterRegistry)

    private val extractedImportanceScore =
        DistributionSummary
            .builder("memory.extracted.importance")
            .description("Importance score for extracted memories")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    override fun recordExtractionTriggered() {
        extractionTriggeredCounter.increment()
    }

    override fun recordExtractionSuccess(count: Int) {
        extractionSuccessCounter.increment(count.toDouble())
    }

    override fun recordExtractionFailure() {
        extractionFailureCounter.increment()
    }

    override fun recordExtractedMemoryType(
        type: String,
        count: Int,
    ) {
        Counter
            .builder("memory.extracted.count")
            .tag("type", type.lowercase())
            .description("Number of extracted memories by type")
            .register(meterRegistry)
            .increment(count.toDouble())
    }

    override fun recordExtractedImportance(importance: Double) {
        extractedImportanceScore.record(importance)
    }
}
