package com.study.webflux.rag.infrastructure.dialogue.config;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

import com.study.webflux.rag.domain.dialogue.port.TtsPort;
import com.study.webflux.rag.domain.voice.model.Voice;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.LoadBalancedSupertoneTtsAdapter;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.SupertoneConfig;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;

@Configuration
public class TtsConfiguration {

	@Bean
	public SupertoneConfig supertoneConfig(RagDialogueProperties properties) {
		var supertone = properties.getSupertone();
		if (supertone.getEndpoints().isEmpty()) {
			throw new IllegalStateException("최소 하나 이상의 TTS 엔드포인트를 설정해야 합니다");
		}
		var firstEndpoint = supertone.getEndpoints().get(0);
		return new SupertoneConfig(firstEndpoint.getApiKey(), firstEndpoint.getBaseUrl());
	}

	@Bean
	public TtsLoadBalancer ttsLoadBalancer(RagDialogueProperties properties) {
		var supertone = properties.getSupertone();
		List<TtsEndpoint> endpoints = supertone.getEndpoints().stream()
			.map(config -> new TtsEndpoint(config.getId(), config.getApiKey(), config.getBaseUrl()))
			.collect(Collectors.toList());

		TtsLoadBalancer loadBalancer = new TtsLoadBalancer(endpoints);
		loadBalancer.setFailureEventPublisher(event -> {
			System.err.println("TTS 엔드포인트 영구 장애 발생: " + event);
		});

		return loadBalancer;
	}

	@Bean
	@Primary
	public TtsPort ttsPort(WebClient.Builder webClientBuilder,
		TtsLoadBalancer loadBalancer,
		Voice voice) {
		return new LoadBalancedSupertoneTtsAdapter(webClientBuilder, loadBalancer, voice);
	}
}
