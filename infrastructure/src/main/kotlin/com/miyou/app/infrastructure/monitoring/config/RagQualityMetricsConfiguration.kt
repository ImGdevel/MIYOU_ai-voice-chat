package com.miyou.app.infrastructure.monitoring.config

import com.miyou.app.application.monitoring.port.RagQualityMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class RagQualityMetricsConfiguration(
    private val meterRegistry: MeterRegistry,
) : RagQualityMetricsPort {
    private val memoryCandidateCounter =
        Counter
            .builder("rag.memory.candidate.count")
            .description("Number of candidate memories before filtering")
            .register(meterRegistry)

    private val memoryFilteredCounter =
        Counter
            .builder("rag.memory.filtered.count")
            .description("Number of memories filtered out")
            .register(meterRegistry)

    private val memorySimilarityScore =
        DistributionSummary
            .builder("rag.memory.similarity.score")
            .description("Vector similarity score for retrieved memories")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    private val memoryImportanceScore =
        DistributionSummary
            .builder("rag.memory.importance")
            .description("Importance score for retrieved memories")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    private val documentRelevanceScore =
        DistributionSummary
            .builder("rag.document.relevance.score")
            .description("Relevance score for retrieved documents")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    override fun recordMemoryCandidateCount(count: Int) {
        memoryCandidateCounter.increment(count.toDouble())
    }

    override fun recordMemoryFilteredCount(count: Int) {
        memoryFilteredCounter.increment(count.toDouble())
    }

    override fun recordMemorySimilarityScore(score: Double) {
        memorySimilarityScore.record(score)
    }

    override fun recordMemoryImportanceScore(importance: Double) {
        memoryImportanceScore.record(importance)
    }

    override fun recordDocumentRelevanceScore(score: Double) {
        documentRelevanceScore.record(score)
    }
}
