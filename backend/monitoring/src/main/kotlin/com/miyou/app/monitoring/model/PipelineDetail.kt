package com.miyou.app.monitoring.model

data class PipelineDetail(
    val pipelineId: String,
    val performance: PerformanceMetrics,
    val usage: UsageAnalytics,
)
