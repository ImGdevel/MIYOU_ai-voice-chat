package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

private const val CREDIT_THRESHOLD = 10.0
private const val TRAFFIC_EXPONENT = 4

class EndpointWeightCalculator {
    fun calculate(
        endpoint: TtsEndpoint,
        maxCreditsInPool: Double,
    ): Double {
        val creditWeight = calculateCreditWeight(endpoint.credits, maxCreditsInPool)
        if (creditWeight <= 0.0) {
            return 0.0
        }

        val trafficWeight =
            calculateTrafficWeight(
                endpoint.activeRequests,
                endpoint.maxConcurrentRequests,
            )

        return creditWeight * trafficWeight
    }

    private fun calculateCreditWeight(
        credits: Double,
        maxCreditsInPool: Double,
    ): Double {
        if (credits <= CREDIT_THRESHOLD) {
            return 0.0
        }

        val denominator = maxCreditsInPool - CREDIT_THRESHOLD
        if (denominator <= 0.0) {
            return 1.0
        }

        val weight = (maxCreditsInPool - credits) / denominator
        return weight.coerceIn(0.0, 1.0)
    }

    private fun calculateTrafficWeight(
        activeRequests: Int,
        maxConcurrentRequests: Int,
    ): Double {
        if (maxConcurrentRequests <= 0) {
            return 0.0
        }

        val ratio = activeRequests.toDouble() / maxConcurrentRequests.toDouble()
        return (1.0 - Math.pow(ratio, TRAFFIC_EXPONENT.toDouble())).coerceAtLeast(0.0)
    }
}
