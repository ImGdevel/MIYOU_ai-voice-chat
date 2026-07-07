package com.miyou.app.monitoring.port

import com.miyou.app.monitoring.model.PipelineSummary

interface PipelineMetricsReporter {
    fun report(summary: PipelineSummary)
}
