package com.miyou.app.infrastructure.monitoring.config

import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class TtsEndpointMetricsRegistrar(
    private val ttsLoadBalancer: TtsLoadBalancer,
    private val ttsBackpressureMetrics: TtsBackpressureMetrics,
) {
    @PostConstruct
    fun register() {
        ttsBackpressureMetrics.registerEndpoints(ttsLoadBalancer.endpoints)
    }
}
