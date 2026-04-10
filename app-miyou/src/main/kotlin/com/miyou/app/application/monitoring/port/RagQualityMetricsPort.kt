package com.miyou.app.application.monitoring.port

interface RagQualityMetricsPort {
    fun recordMemoryCandidateCount(count: Int)

    fun recordMemoryFilteredCount(count: Int)

    fun recordMemorySimilarityScore(score: Double)

    fun recordMemoryImportanceScore(importance: Double)

    fun recordDocumentRelevanceScore(score: Double)
}
