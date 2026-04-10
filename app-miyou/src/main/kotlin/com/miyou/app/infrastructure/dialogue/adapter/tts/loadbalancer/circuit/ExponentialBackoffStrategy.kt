package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit

import java.time.Duration

data class ExponentialBackoffStrategy(
    val baseDelay: Duration,
    val maxDelay: Duration,
) {
    init {
        require(!baseDelay.isZero && !baseDelay.isNegative) {
            "baseDelay must be positive"
        }
        require(!maxDelay.isNegative && maxDelay >= baseDelay) {
            "maxDelay must be greater than or equal to baseDelay"
        }
    }

    fun calculateDelay(failureCount: Int): Duration {
        if (failureCount <= 0) {
            return Duration.ZERO
        }

        val multiplier = 1L shl (failureCount - 1)
        val delaySeconds = baseDelay.seconds * multiplier
        return Duration.ofSeconds(minOf(delaySeconds, maxDelay.seconds))
    }
}
