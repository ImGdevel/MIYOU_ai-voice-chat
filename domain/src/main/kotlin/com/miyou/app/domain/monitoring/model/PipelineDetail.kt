package com.miyou.app.domain.monitoring.model

data class PipelineDetail(
    val pipelineId: String,
    val performance: PerformanceMetrics,
    val usage: UsageAnalytics,
)
