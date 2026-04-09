package com.miyou.app.domain.monitoring.model

import java.time.Instant

data class StagePerformanceRollup(
    val bucketStart: Instant,
    val granularity: MetricsGranularity,
    val stageName: String,
    val count: Long,
    val totalDurationMillis: Long,
    val avgDurationMillis: Double,
)
