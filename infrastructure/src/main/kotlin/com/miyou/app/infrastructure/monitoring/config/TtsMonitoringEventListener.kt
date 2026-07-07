package com.miyou.app.infrastructure.monitoring.config

import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpointFailureEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class TtsMonitoringEventListener(
    private val ttsBackpressureMetrics: TtsBackpressureMetrics,
) {
    @EventListener
    fun handleFailureEvent(event: TtsEndpointFailureEvent) {
        ttsBackpressureMetrics.recordEndpointFailure(
            event.endpointId,
            event.errorType,
            event.errorMessage,
        )
    }
}
