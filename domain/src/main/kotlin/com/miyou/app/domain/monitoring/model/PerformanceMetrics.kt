package com.miyou.app.domain.monitoring.model

import java.time.Instant

data class PerformanceMetrics(
    val pipelineId: String,
    val status: String,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val totalDurationMillis: Long,
    val firstResponseLatencyMillis: Long?,
    val lastResponseLatencyMillis: Long?,
    val stages: List<StagePerformance>,
    val systemAttributes: Map<String, Any?>,
) {
    data class StagePerformance(
        val stageName: String,
        val status: String,
        val startedAt: Instant?,
        val finishedAt: Instant?,
        val durationMillis: Long,
        val attributes: Map<String, Any?>,
    )

    companion object {
        fun fromPipelineSummary(
            pipelineId: String,
            status: String,
            startedAt: Instant?,
            finishedAt: Instant?,
            durationMillis: Long,
            firstResponseLatencyMillis: Long?,
            lastResponseLatencyMillis: Long?,
            stages: List<StagePerformance>,
            systemAttributes: Map<String, Any?>,
        ): PerformanceMetrics =
            PerformanceMetrics(
                pipelineId,
                status,
                startedAt,
                finishedAt,
                durationMillis,
                firstResponseLatencyMillis,
                lastResponseLatencyMillis,
                stages,
                systemAttributes,
            )
    }
}
