package com.miyou.app.infrastructure.outbound.monitoring

import com.miyou.app.domain.monitoring.model.PipelineSummary
import com.miyou.app.domain.monitoring.port.PipelineMetricsReporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoggingPipelineMetricsReporter : PipelineMetricsReporter {
    private val log = LoggerFactory.getLogger(LoggingPipelineMetricsReporter::class.java)

    override fun report(summary: PipelineSummary) {
        val ideaActive = System.getProperty("idea.active", "false").toBoolean()
        val stageSummary = formatStages(summary, ideaActive)
        val llmOutputs = formatOutputs(summary, ideaActive)

        if (ideaActive) {
            log.info(
                """
                Dialogue pipeline ${summary.pipelineId}
                status=${summary.status} duration=${summary.durationMillis()}ms
                firstLatency=${safeLatency(summary.firstResponseLatencyMillis)}ms
                lastLatency=${safeLatency(summary.lastResponseLatencyMillis)}ms
                attributes=${summary.attributes}
                stages:
                $stageSummary
                llmResults:
                $llmOutputs
                """.trimIndent(),
            )
        } else {
            log.info(
                "Dialogue pipeline {} status={} duration={}ms firstLatency={}ms lastLatency={}ms attributes={} stages=[{}] llmResults={}",
                summary.pipelineId,
                summary.status,
                summary.durationMillis(),
                safeLatency(summary.firstResponseLatencyMillis),
                safeLatency(summary.lastResponseLatencyMillis),
                summary.attributes,
                stageSummary,
                llmOutputs,
            )
        }
    }

    private fun safeLatency(latency: Long?): String = latency?.toString() ?: "-"

    private fun formatStages(
        summary: PipelineSummary,
        ideaActive: Boolean,
    ): String {
        val stream =
            summary.stages.map { stage ->
                "${stage.stage}:${stage.status}(${stage.durationMillis}ms, attrs=${stage.attributes})"
            }
        return if (ideaActive) {
            stream.joinToString(separator = System.lineSeparator() + "  ", prefix = "  ", postfix = "")
        } else {
            stream.joinToString(separator = ", ")
        }
    }

    private fun formatOutputs(
        summary: PipelineSummary,
        ideaActive: Boolean,
    ): String {
        if (summary.llmOutputs.isEmpty()) {
            return "(none)"
        }
        val stream = summary.llmOutputs
        return if (ideaActive) {
            stream.joinToString(
                separator = System.lineSeparator() + "  - ",
                prefix = "  - ",
                postfix = "",
            )
        } else {
            stream.joinToString(separator = " | ")
        }
    }
}
