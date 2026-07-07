package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.EndpointCircuitBreaker
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.ExponentialBackoffStrategy
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class TtsEndpoint(
    val id: String,
    val apiKey: String,
    val baseUrl: String,
    val maxConcurrentRequests: Int = 10,
    backoffStrategy: ExponentialBackoffStrategy =
        ExponentialBackoffStrategy(
            Duration.ofSeconds(5),
            Duration.ofSeconds(300),
        ),
) {
    private val healthLock = Any()
    private val activeRequestCounter = AtomicInteger(0)
    private val circuitBreakerInternal = EndpointCircuitBreaker(backoffStrategy)

    @Volatile
    private var circuitOpenedAtInternal: Instant? = null
    private var creditsValue: Double = Double.MAX_VALUE

    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(maxConcurrentRequests > 0) { "maxConcurrentRequests must be positive" }
    }

    var health: EndpointHealth = EndpointHealth.HEALTHY
        set(value) {
            synchronized(healthLock) {
                field = value
                when (value) {
                    EndpointHealth.TEMPORARY_FAILURE, EndpointHealth.PERMANENT_FAILURE -> {
                        circuitOpenedAtInternal = Instant.now()
                    }

                    EndpointHealth.HEALTHY -> {
                        circuitOpenedAtInternal = null
                    }

                    EndpointHealth.CLIENT_ERROR -> {}
                }
            }
        }

    constructor(id: String, apiKey: String, baseUrl: String) : this(
        id = id,
        apiKey = apiKey,
        baseUrl = baseUrl,
        maxConcurrentRequests = 10,
        backoffStrategy = ExponentialBackoffStrategy(Duration.ofSeconds(5), Duration.ofSeconds(300)),
    )

    constructor(id: String, apiKey: String, baseUrl: String, maxConcurrentRequests: Int) : this(
        id = id,
        apiKey = apiKey,
        baseUrl = baseUrl,
        maxConcurrentRequests = maxConcurrentRequests,
        backoffStrategy = ExponentialBackoffStrategy(Duration.ofSeconds(5), Duration.ofSeconds(300)),
    )

    val activeRequests: Int
        get() = activeRequestCounter.get()

    val circuitOpenedAt: Instant?
        get() = circuitOpenedAtInternal

    val circuitBreaker: EndpointCircuitBreaker
        get() = circuitBreakerInternal

    val credits: Double
        get() = creditsValue

    fun isHealthy(): Boolean = health == EndpointHealth.HEALTHY

    fun incrementActiveRequests(): Int = activeRequestCounter.incrementAndGet()

    fun decrementActiveRequests(): Int = activeRequestCounter.decrementAndGet()

    fun updateCredits(newCredits: Double) {
        creditsValue = newCredits
    }

    fun canAcceptRequest(): Boolean =
        health == EndpointHealth.HEALTHY &&
            activeRequests < maxConcurrentRequests &&
            circuitBreakerInternal.allowRequest()

    enum class EndpointHealth {
        HEALTHY,
        TEMPORARY_FAILURE,
        PERMANENT_FAILURE,
        CLIENT_ERROR,
    }

    enum class FailureType {
        TEMPORARY,
        PERMANENT,
        CLIENT_ERROR,
    }
}
