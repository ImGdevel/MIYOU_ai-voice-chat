package com.miyou.app.domain.monitoring.model

import java.time.Duration
import java.time.Instant

data class PipelineSummary(
    val pipelineId: String,
    val status: PipelineStatus,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val attributes: Map<String, Any?>,
    val stages: List<StageSnapshot>,
    val llmOutputs: List<String>,
    val firstResponseLatencyMillis: Long?,
    val lastResponseLatencyMillis: Long?,
) {
    fun durationMillis(): Long =
        if (startedAt == null || finishedAt == null) {
            -1L
        } else {
            Duration.between(startedAt, finishedAt).toMillis()
        }
}
