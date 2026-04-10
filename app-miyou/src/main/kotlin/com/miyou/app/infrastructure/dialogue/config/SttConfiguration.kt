package com.miyou.app.infrastructure.dialogue.config

import com.miyou.app.domain.dialogue.port.SttPort
import com.miyou.app.infrastructure.dialogue.adapter.stt.OpenAiWhisperSttAdapter
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SttConfiguration {
    @Bean
    fun sttPort(
        webClientBuilder: WebClient.Builder,
        properties: RagDialogueProperties,
        @Value("\${spring.ai.openai.api-key:}") springOpenAiApiKey: String,
        @Value("\${spring.ai.openai.base-url:https://api.openai.com}") springOpenAiBaseUrl: String,
    ): SttPort {
        val apiKey = resolveApiKey(properties, springOpenAiApiKey)
        val baseUrl = resolveBaseUrl(properties, springOpenAiBaseUrl)
        val model = properties.stt.model
        return OpenAiWhisperSttAdapter(webClientBuilder, apiKey, baseUrl, model)
    }

    private fun resolveApiKey(
        properties: RagDialogueProperties,
        springOpenAiApiKey: String,
    ): String {
        val configuredApiKey = properties.openai.apiKey
        if (!configuredApiKey.isNullOrBlank()) {
            return configuredApiKey
        }

        if (springOpenAiApiKey.isBlank()) {
            throw IllegalStateException("OpenAI API key가 구성되어 있지 않습니다.")
        }

        return springOpenAiApiKey
    }

    private fun resolveBaseUrl(
        properties: RagDialogueProperties,
        springOpenAiBaseUrl: String,
    ): String {
        val configuredBaseUrl = properties.openai.baseUrl
        return if (!configuredBaseUrl.isNullOrBlank() && configuredBaseUrl != "https://api.openai.com/v1") {
            configuredBaseUrl
        } else {
            springOpenAiBaseUrl
        }
    }
}
