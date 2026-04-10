package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class TtsCreditMonitor(
    private val loadBalancer: TtsLoadBalancer,
    private val webClientBuilder: WebClient.Builder,
    private val eventPublisher: ApplicationEventPublisher,
    properties: RagDialogueProperties,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val config: RagDialogueProperties.CreditMonitorConfig = properties.supertone.creditMonitor
    private val creditGauges = ConcurrentHashMap<String, Gauge>()
    private val circuitStateGauges = ConcurrentHashMap<String, Gauge>()
    private val webClients = ConcurrentHashMap<String, WebClient>()

    init {
        registerMetrics()
    }

    private fun registerMetrics() {
        loadBalancer.endpoints.forEach { endpoint ->
            creditGauges[endpoint.id] =
                Gauge
                    .builder(
                        "tts.endpoint.credits",
                        endpoint,
                        TtsEndpoint::credits,
                    ).tag("endpoint", endpoint.id)
                    .description("TTS endpoint credit balance")
                    .register(meterRegistry)

            circuitStateGauges[endpoint.id] =
                Gauge
                    .builder(
                        "tts.endpoint.circuit_state",
                        endpoint,
                    ) { e ->
                        mapCircuitState(e.circuitBreaker.state)
                    }.tag("endpoint", endpoint.id)
                    .description("TTS endpoint circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                    .register(meterRegistry)
        }
    }

    private fun mapCircuitState(
        state: com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.CircuitBreakerState,
    ): Double =
        when (state) {
            com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.CircuitBreakerState.CLOSED -> 0.0
            com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.CircuitBreakerState.OPEN -> 1.0
            com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.CircuitBreakerState.HALF_OPEN -> 2.0
        }

    @Scheduled(fixedDelayString = "#{${'$'}{rag.dialogue.supertone.credit-monitor.poll-interval-seconds:45} * 1000}")
    fun pollAllEndpoints() {
        if (!config.enabled) {
            return
        }

        val endpoints = loadBalancer.endpoints
        log.debug("크레딧 모니터링 시작: {}개 엔드포인트", endpoints.size)

        endpoints.forEach { endpoint ->
            pollEndpointCredits(endpoint)
                .subscribe(
                    { credits -> handleCreditUpdate(endpoint, credits) },
                    { error ->
                        log.warn("엔드포인트 {} 크레딧 조회 실패: {}", endpoint.id, error.message)
                    },
                )
        }
    }

    private fun pollEndpointCredits(endpoint: TtsEndpoint): Mono<Double> {
        val webClient = getWebClient(endpoint)
        return webClient
            .get()
            .uri("/v1/credits")
            .retrieve()
            .bodyToMono(CreditResponse::class.java)
            .map(CreditResponse::credits)
            .timeout(Duration.ofSeconds(5))
            .doOnError { error ->
                log.debug("엔드포인트 {} 크레딧 조회 오류: {}", endpoint.id, error.message)
            }
    }

    private fun getWebClient(endpoint: TtsEndpoint): WebClient =
        webClients.computeIfAbsent(endpoint.id) {
            webClientBuilder
                .clone()
                .baseUrl(endpoint.baseUrl)
                .defaultHeader("Authorization", "Bearer ${endpoint.apiKey}")
                .build()
        }

    private fun handleCreditUpdate(
        endpoint: TtsEndpoint,
        credits: Double?,
    ) {
        if (credits == null) {
            return
        }

        endpoint.updateCredits(credits)
        log.debug("엔드포인트 {} 최신 크레딧: {}", endpoint.id, credits)

        if (credits < config.lowCreditThreshold) {
            log.warn("엔드포인트 {} 크레딧 부족: {} < {}", endpoint.id, credits, config.lowCreditThreshold)
            endpoint.health = TtsEndpoint.EndpointHealth.PERMANENT_FAILURE

            eventPublisher.publishEvent(
                TtsLowCreditEvent(
                    endpointId = endpoint.id,
                    remainingCredits = credits,
                    threshold = config.lowCreditThreshold,
                ),
            )
        }
    }
}
