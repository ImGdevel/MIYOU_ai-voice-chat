package com.miyou.app.domain.monitoring.model

data class StagePerformanceSummary(
    val stageName: String,
    val avgDurationMillis: Double,
)
