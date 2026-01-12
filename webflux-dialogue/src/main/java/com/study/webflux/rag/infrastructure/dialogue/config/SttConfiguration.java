package com.study.webflux.rag.infrastructure.dialogue.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.study.webflux.rag.domain.dialogue.port.SttPort;
import com.study.webflux.rag.infrastructure.dialogue.adapter.stt.OpenAiWhisperSttAdapter;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;

/** Whisper 기반 STT 구성을 제공합니다. */
@Configuration
public class SttConfiguration {

	/** OpenAI Whisper STT 포트를 생성합니다. */
	@Bean
	public SttPort sttPort(WebClient.Builder webClientBuilder,
		RagDialogueProperties properties,
		@Value("${spring.ai.openai.api-key:}") String springOpenAiApiKey,
		@Value("${spring.ai.openai.base-url:https://api.openai.com}") String springOpenAiBaseUrl) {
		String apiKey = resolveApiKey(properties, springOpenAiApiKey);
		String baseUrl = resolveBaseUrl(properties, springOpenAiBaseUrl);
		String model = properties.getStt().getModel();
		return new OpenAiWhisperSttAdapter(webClientBuilder, apiKey, baseUrl, model);
	}

	private String resolveApiKey(RagDialogueProperties properties, String springOpenAiApiKey) {
		String configuredApiKey = properties.getOpenai().getApiKey();
		if (configuredApiKey != null && !configuredApiKey.isBlank()) {
			return configuredApiKey;
		}
		if (springOpenAiApiKey == null || springOpenAiApiKey.isBlank()) {
			throw new IllegalStateException("OpenAI API 키가 설정되지 않았습니다");
		}
		return springOpenAiApiKey;
	}

	private String resolveBaseUrl(RagDialogueProperties properties, String springOpenAiBaseUrl) {
		String configuredBaseUrl = properties.getOpenai().getBaseUrl();
		if (configuredBaseUrl != null
			&& !configuredBaseUrl.isBlank()
			&& !"https://api.openai.com/v1".equals(configuredBaseUrl)) {
			return configuredBaseUrl;
		}
		return springOpenAiBaseUrl;
	}
}
