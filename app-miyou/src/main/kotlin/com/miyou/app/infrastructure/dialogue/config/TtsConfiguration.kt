package com.miyou.app.infrastructure.dialogue.config

import com.miyou.app.domain.dialogue.port.TtsPort
import com.miyou.app.domain.voice.model.Voice
import com.miyou.app.infrastructure.common.constants.DialogueConstants
import com.miyou.app.infrastructure.dialogue.adapter.tts.LoadBalancedSupertoneTtsAdapter
import com.miyou.app.infrastructure.dialogue.adapter.tts.SupertoneConfig
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableScheduling
class TtsConfiguration {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun supertoneConfig(properties: RagDialogueProperties): SupertoneConfig {
        val supertone = properties.supertone
        if (supertone.endpoints.isEmpty()) {
            throw IllegalStateException("초기화 실패: 최소 하나 이상의 TTS 엔드포인트가 필요합니다.")
        }

        val firstEndpoint = supertone.endpoints.first()
        val apiKey = requireNotNull(firstEndpoint.apiKey) { "supertone endpoint API key is required" }
        return SupertoneConfig(apiKey, firstEndpoint.baseUrl)
    }

    @Bean
    fun ttsLoadBalancer(
        properties: RagDialogueProperties,
        eventPublisher: ApplicationEventPublisher,
    ): TtsLoadBalancer {
        val endpoints =
            properties.supertone.endpoints.map { config ->
                val id = requireNotNull(config.id) { "supertone endpoint id is required" }
                val apiKey = requireNotNull(config.apiKey) { "supertone endpoint API key is required for ${config.id}" }
                TtsEndpoint(
                    id,
                    apiKey,
                    config.baseUrl.ifBlank { DialogueConstants.Supertone.BASE_URL },
                    config.maxConcurrentRequests,
                )
            }

        val loadBalancer = TtsLoadBalancer(endpoints)
        loadBalancer.setFailureEventPublisher { event ->
            eventPublisher.publishEvent(event)
            if (event.errorType == "PERMANENT_FAILURE") {
                log.error("TTS 엔드포인트 {} 영구 실패 감지: {}", event.endpointId, event)
            } else {
                log.warn("TTS 엔드포인트 {} 일시 실패: {}", event.endpointId, event)
            }
        }
        return loadBalancer
    }

    @Bean
    @Primary
    fun ttsPort(
        webClientBuilder: WebClient.Builder,
        loadBalancer: TtsLoadBalancer,
        voice: Voice,
    ): TtsPort = LoadBalancedSupertoneTtsAdapter(webClientBuilder, loadBalancer, voice)
}
