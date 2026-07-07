package com.miyou.app.domain.monitoring.port

import com.miyou.app.domain.monitoring.model.PipelineSummary

interface PipelineMetricsReporter {
    fun report(summary: PipelineSummary)
}
