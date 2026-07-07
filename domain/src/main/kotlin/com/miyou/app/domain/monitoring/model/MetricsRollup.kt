package com.miyou.app.domain.monitoring.model

import java.time.Instant

data class MetricsRollup(
    val bucketStart: Instant,
    val granularity: MetricsGranularity,
    val requestCount: Long,
    val totalTokens: Long,
    val totalDurationMillis: Long,
    val avgResponseMillis: Double,
)
