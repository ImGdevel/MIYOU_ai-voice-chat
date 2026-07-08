package com.miyou.app.monitoring.monitor

import com.miyou.app.monitoring.port.PipelineMetricsReporter
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class DialoguePipelineMonitor(
    private val reporter: PipelineMetricsReporter,
    private val clock: Clock,
) {
    fun create(
        sessionId: String?,
        userId: String?,
        personaId: String?,
        inputText: String?,
    ): DialoguePipelineTracker = DialoguePipelineTracker(sessionId, userId, personaId, inputText, reporter, clock)
}
