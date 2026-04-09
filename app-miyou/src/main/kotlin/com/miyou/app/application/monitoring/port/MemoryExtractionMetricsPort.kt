package com.miyou.app.application.monitoring.port

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
