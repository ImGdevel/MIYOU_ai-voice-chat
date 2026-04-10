package com.miyou.app.domain.monitoring.model

import java.time.Instant

data class StageSnapshot(
    val stage: DialoguePipelineStage,
    val status: StageStatus,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val durationMillis: Long,
    val attributes: Map<String, Any?>,
) {
    init {
        requireNotNull(attributes) { "attributes must not be null" }
    }
}
