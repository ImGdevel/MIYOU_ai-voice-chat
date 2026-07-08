package com.miyou.app.monitoring.model

import java.time.Duration
import java.time.Instant

data class PipelineSummary(
    val pipelineId: String,
    val sessionId: String?,
    val userId: String?,
    val personaId: String?,
    val status: PipelineStatus,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val attributes: Map<String, Any?>,
    val stages: List<StageSnapshot>,
    val llmOutputs: List<String>,
    val firstResponseLatencyMillis: Long?,
    val lastResponseLatencyMillis: Long?,
    val firstTokenLatencyMillis: Long?,
) {
    fun durationMillis(): Long =
        if (startedAt == null || finishedAt == null) {
            -1L
        } else {
            Duration.between(startedAt, finishedAt).toMillis()
        }
}
