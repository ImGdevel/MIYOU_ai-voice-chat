package com.miyou.app.infrastructure.monitoring.config

import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint.EndpointHealth
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TtsMetricsConfiguration {
    @Bean
    fun ttsEndpointMetrics(loadBalancer: TtsLoadBalancer): MeterBinder =
        MeterBinder { registry ->
            loadBalancer.endpoints.forEach { endpoint ->
                Gauge
                    .builder("tts.endpoint.active.requests", endpoint) { it.activeRequests.toDouble() }
                    .description("TTS endpoint active requests")
                    .tag("endpoint", endpoint.id)
                    .register(registry)

                Gauge
                    .builder("tts.endpoint.health", endpoint) { ep -> healthToNumber(ep.health) }
                    .description("TTS endpoint health status (1=HEALTHY, 0=TEMP_FAIL, -1=PERM_FAIL)")
                    .tag("endpoint", endpoint.id)
                    .register(registry)
            }
        }

    private fun healthToNumber(health: EndpointHealth): Double =
        when (health) {
            EndpointHealth.HEALTHY -> 1.0
            EndpointHealth.TEMPORARY_FAILURE -> 0.0
            EndpointHealth.PERMANENT_FAILURE -> -1.0
            EndpointHealth.CLIENT_ERROR -> 0.0
        }
}
