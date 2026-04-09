package com.miyou.app.infrastructure.monitoring.micrometer

import com.miyou.app.domain.monitoring.model.PipelineSummary
import com.miyou.app.domain.monitoring.port.PipelineMetricsReporter

/**
 * 여러 [PipelineMetricsReporter]를 순차적으로 실행해 단일 진입점으로 집계 결과를 기록.
 */
class CompositePipelineMetricsReporter(
    private val reporters: List<PipelineMetricsReporter>,
) : PipelineMetricsReporter {
    init {
        require(reporters.isNotEmpty()) { "reporters must not be empty" }
    }

    override fun report(summary: PipelineSummary) {
        reporters.forEach { reporter ->
            reporter.report(summary)
        }
    }
}
