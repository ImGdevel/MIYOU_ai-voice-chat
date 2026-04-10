package com.miyou.app.infrastructure.monitoring.config

import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

@Component
class TtsBackpressureMetrics(
    private val meterRegistry: MeterRegistry,
) {
    private val registeredEndpoints = ConcurrentHashMap<String, TtsEndpoint>()
    private val lastFailureCodes = ConcurrentHashMap<String, AtomicInteger>()

    private data class FailureReason(
        val reasonTag: String,
        val numericCode: Int,
    )

    fun registerEndpoints(endpoints: List<TtsEndpoint>) {
        endpoints.forEach { endpoint ->
            if (!registeredEndpoints.containsKey(endpoint.id)) {
                registeredEndpoints[endpoint.id] = endpoint
                lastFailureCodes[endpoint.id] = AtomicInteger(NO_FAILURE_CODE)

                Gauge
                    .builder("tts.endpoint.queue.size", endpoint) { it.activeRequests.toDouble() }
                    .tag("endpoint", endpoint.id)
                    .description("Number of active requests for TTS endpoint")
                    .register(meterRegistry)

                Gauge
                    .builder("tts.endpoint.health", endpoint) { e ->
                        when (e.health) {
                            TtsEndpoint.EndpointHealth.HEALTHY -> HEALTHY_CODE
                            TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE -> TEMPORARY_FAILURE_CODE
                            TtsEndpoint.EndpointHealth.PERMANENT_FAILURE -> PERMANENT_FAILURE_CODE
                            TtsEndpoint.EndpointHealth.CLIENT_ERROR -> CLIENT_ERROR_CODE
                        }
                    }.tag("endpoint", endpoint.id)
                    .description("TTS endpoint health status")
                    .register(meterRegistry)

                Gauge
                    .builder(
                        "tts.endpoint.last.failure.code",
                        lastFailureCodes.getValue(endpoint.id),
                    ) { it.get().toDouble() }
                    .tag("endpoint", endpoint.id)
                    .description("TTS endpoint last failure code (0=none, HTTP status or negative internal code)")
                    .register(meterRegistry)
            }
        }
    }

    fun updateEndpoints(endpoints: List<TtsEndpoint>) {
        registerEndpoints(endpoints)
    }

    fun recordEndpointFailure(
        endpointId: String,
        errorType: String,
        errorMessage: String?,
    ) {
        val reason = classifyReason(errorMessage)
        Counter
            .builder("tts.endpoint.failure.total")
            .tag("endpoint", endpointId)
            .tag("error_type", normalizeErrorType(errorType))
            .tag("reason_code", reason.reasonTag)
            .description("TTS endpoint failures by endpoint and reason")
            .register(meterRegistry)
            .increment()

        lastFailureCodes
            .computeIfAbsent(endpointId) { AtomicInteger(NO_FAILURE_CODE) }
            .set(reason.numericCode)
    }

    private fun normalizeErrorType(errorType: String): String = if (errorType.isBlank()) "UNKNOWN" else errorType

    private fun classifyReason(errorMessage: String?): FailureReason {
        if (errorMessage.isNullOrBlank()) {
            return FailureReason("UNKNOWN", UNKNOWN_FAILURE_CODE)
        }

        val matcher = HTTP_STATUS_PATTERN.matcher(errorMessage)
        if (matcher.find()) {
            val status = matcher.group(1).toInt()
            return FailureReason("HTTP_$status", status)
        }

        val message = errorMessage.lowercase()
        return when {
            message.contains("timeout") -> {
                FailureReason("TIMEOUT", TIMEOUT_FAILURE_CODE)
            }

            message.contains("connection refused") || message.contains("connection reset") ||
                message.contains("ioexception") -> {
                FailureReason("NETWORK", NETWORK_FAILURE_CODE)
            }

            else -> {
                FailureReason("UNKNOWN", UNKNOWN_FAILURE_CODE)
            }
        }
    }

    companion object {
        private val HTTP_STATUS_PATTERN = Pattern.compile("\\[(\\d{3})]")
        private const val NO_FAILURE_CODE = 0
        private const val UNKNOWN_FAILURE_CODE = -1
        private const val TIMEOUT_FAILURE_CODE = -408
        private const val NETWORK_FAILURE_CODE = -503
        private const val HEALTHY_CODE = 1.0
        private const val TEMPORARY_FAILURE_CODE = 0.0
        private const val PERMANENT_FAILURE_CODE = -1.0
        private const val CLIENT_ERROR_CODE = -2.0
    }
}
