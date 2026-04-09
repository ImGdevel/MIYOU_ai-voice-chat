package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

class TtsLoadBalancer(
    private val endpointList: List<TtsEndpoint>,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val roundRobinIndex = AtomicInteger(0)
    private val weightCalculator = EndpointWeightCalculator()
    private val temporaryRecoveryInterval = Duration.ofSeconds(30)
    private val recoveryCheckIntervalNanos = Duration.ofSeconds(10).toNanos()
    private var failureEventPublisher: Consumer<TtsEndpointFailureEvent>? = null
    private var lastRecoveryCheckTime = System.nanoTime()

    val endpoints: List<TtsEndpoint> = endpointList

    init {
        require(endpoints.isNotEmpty()) { "At least one TTS endpoint is required." }
    }

    fun setFailureEventPublisher(publisher: Consumer<TtsEndpointFailureEvent>) {
        failureEventPublisher = publisher
    }

    fun selectEndpoint(): TtsEndpoint {
        val currentTime = System.nanoTime()
        if (currentTime - lastRecoveryCheckTime > recoveryCheckIntervalNanos) {
            tryRecoverTemporaryFailures()
            lastRecoveryCheckTime = currentTime
        }

        val eligible = endpointList.filter(TtsEndpoint::canAcceptRequest)
        if (eligible.isEmpty()) {
            return selectFallbackEndpoint()
        }

        val maxCredits = eligible.maxOfOrNull(TtsEndpoint::credits) ?: 1.0
        val weights = DoubleArray(eligible.size)
        var totalWeight = 0.0

        eligible.forEachIndexed { index, endpoint ->
            val weight = weightCalculator.calculate(endpoint, maxCredits)
            weights[index] = weight
            totalWeight += weight
        }

        if (totalWeight <= 0.0) {
            val idx = (roundRobinIndex.getAndIncrement() and Int.MAX_VALUE) % eligible.size
            return eligible[idx]
        }

        val randomValue = ThreadLocalRandom.current().nextDouble() * totalWeight
        var cumulative = 0.0
        eligible.forEachIndexed { index, endpoint ->
            cumulative += weights[index]
            if (randomValue <= cumulative) {
                return endpoint
            }
        }

        return eligible[eligible.size - 1]
    }

    private fun selectFallbackEndpoint(): TtsEndpoint {
        endpointList
            .firstOrNull { it.health == TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE }
            ?.let { endpoint ->
                log.warn("No healthy TTS endpoint. Fallback temporary-failure endpoint: {}", endpoint.id)
                return endpoint
            }

        log.error("No available TTS endpoint for fallback")
        throw IllegalStateException("No available TTS endpoint. Fallback failed.")
    }

    private fun tryRecoverTemporaryFailures() {
        val now = Instant.now()
        endpointList
            .filter { it.health == TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE }
            .filter { it.circuitOpenedAt != null }
            .forEach { endpoint ->
                val openedAt = endpoint.circuitOpenedAt
                if (openedAt != null && Duration.between(openedAt, now) > temporaryRecoveryInterval) {
                    log.info("Recovering temporary-failure endpoint={}", endpoint.id)
                    endpoint.health = TtsEndpoint.EndpointHealth.HEALTHY
                }
            }
    }

    fun reportSuccess(endpoint: TtsEndpoint) {
        endpoint.circuitBreaker.recordSuccess()
        if (endpoint.health == TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE) {
            log.info("Endpoint recovered: {}", endpoint.id)
            endpoint.health = TtsEndpoint.EndpointHealth.HEALTHY
        }
    }

    fun reportFailure(
        endpoint: TtsEndpoint,
        error: Throwable,
    ) {
        val failureType = TtsErrorClassifier.classifyError(error)
        endpoint.circuitBreaker.recordFailure(failureType)

        when (failureType) {
            TtsEndpoint.FailureType.TEMPORARY -> handleTemporaryFailure(endpoint, error)
            TtsEndpoint.FailureType.PERMANENT -> handlePermanentFailure(endpoint, error)
            TtsEndpoint.FailureType.CLIENT_ERROR -> handleClientError(endpoint, error)
        }
    }

    private fun handleTemporaryFailure(
        endpoint: TtsEndpoint,
        error: Throwable,
    ) {
        val description = getErrorDescription(error)
        log.warn("Endpoint {} temporary failure: {}", endpoint.id, description)
        endpoint.health = TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE
        publishFailureEvent(endpoint, "TEMPORARY_FAILURE", description)
    }

    private fun handlePermanentFailure(
        endpoint: TtsEndpoint,
        error: Throwable,
    ) {
        val description = getErrorDescription(error)
        log.error("Endpoint {} permanent failure: {}", endpoint.id, description)
        endpoint.health = TtsEndpoint.EndpointHealth.PERMANENT_FAILURE
        publishFailureEvent(endpoint, "PERMANENT_FAILURE", description)
    }

    private fun handleClientError(
        endpoint: TtsEndpoint,
        error: Throwable,
    ) {
        val description = getErrorDescription(error)
        log.warn("Endpoint {} client error ignored: {}", endpoint.id, description)
        publishFailureEvent(endpoint, "CLIENT_ERROR", description)
    }

    private fun publishFailureEvent(
        endpoint: TtsEndpoint,
        errorType: String,
        description: String,
    ) {
        val publisher = failureEventPublisher ?: return
        publisher.accept(TtsEndpointFailureEvent(endpoint.id, errorType, description))
    }

    private fun getErrorDescription(error: Throwable): String =
        when (error) {
            is WebClientResponseException -> {
                "[${error.statusCode.value()}] ${TtsErrorClassifier.getErrorDescription(error.statusCode.value())}"
            }

            else -> {
                error.message ?: "unknown error"
            }
        }
}
