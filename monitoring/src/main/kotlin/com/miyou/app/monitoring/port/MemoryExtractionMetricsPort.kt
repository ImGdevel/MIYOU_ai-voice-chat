package com.miyou.app.monitoring.port

interface MemoryExtractionMetricsPort {
    fun recordExtractionTriggered()

    fun recordExtractionSuccess(count: Int)

    fun recordExtractionFailure()

    fun recordExtractedMemoryType(
        type: String,
        count: Int,
    )

    fun recordExtractedImportance(importance: Double)
}
