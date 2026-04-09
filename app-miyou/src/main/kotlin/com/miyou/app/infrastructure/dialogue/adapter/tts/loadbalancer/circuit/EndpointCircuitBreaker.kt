package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit

import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint
import java.time.Duration
import java.time.Instant

class EndpointCircuitBreaker(
    private val backoffStrategy: ExponentialBackoffStrategy,
) {
    private val stateLock = Any()

    @Volatile
    private var stateInternal: CircuitBreakerState = CircuitBreakerState.CLOSED
        private set

    @Volatile
    private var circuitOpenedAt: Instant? = null

    @Volatile
    private var failureCount: Int = 0

    fun allowRequest(): Boolean =
        when (stateInternal) {
            CircuitBreakerState.CLOSED -> {
                true
            }

            CircuitBreakerState.HALF_OPEN -> {
                true
            }

            CircuitBreakerState.OPEN -> {
                if (failureCount == Int.MAX_VALUE) {
                    false
                } else {
                    val openAt = circuitOpenedAt
                    val backoffDuration = backoffStrategy.calculateDelay(failureCount)

                    if (openAt != null && Duration.between(openAt, Instant.now()) > backoffDuration) {
                        transitionToHalfOpen()
                        true
                    } else {
                        false
                    }
                }
            }
        }

    fun recordSuccess() {
        synchronized(stateLock) {
            if (stateInternal == CircuitBreakerState.HALF_OPEN) {
                stateInternal = CircuitBreakerState.CLOSED
                failureCount = 0
                circuitOpenedAt = null
            }
        }
    }

    fun recordFailure(failureType: TtsEndpoint.FailureType) {
        synchronized(stateLock) {
            when (failureType) {
                TtsEndpoint.FailureType.PERMANENT -> {
                    stateInternal = CircuitBreakerState.OPEN
                    circuitOpenedAt = Instant.now()
                    failureCount = Int.MAX_VALUE
                }

                TtsEndpoint.FailureType.TEMPORARY -> {
                    stateInternal = CircuitBreakerState.OPEN
                    circuitOpenedAt = Instant.now()
                    failureCount = minOf(failureCount + 1, 10)
                }

                TtsEndpoint.FailureType.CLIENT_ERROR -> {}
            }
        }
    }

    private fun transitionToHalfOpen() {
        synchronized(stateLock) {
            if (stateInternal == CircuitBreakerState.OPEN) {
                stateInternal = CircuitBreakerState.HALF_OPEN
            }
        }
    }

    val state: CircuitBreakerState
        get() = stateInternal

    fun getFailureCount(): Int = failureCount

    fun getCircuitOpenedAt(): Instant? = circuitOpenedAt
}
