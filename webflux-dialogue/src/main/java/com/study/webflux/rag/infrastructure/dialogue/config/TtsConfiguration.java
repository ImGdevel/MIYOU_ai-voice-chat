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
import com.study.webflux.rag.infrastructure.monitoring.config.TtsBackpressureMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Supertone 다중 엔드포인트 TTS 구성을 제공합니다. */
@Configuration
public class TtsConfiguration {

	private static final Logger log = LoggerFactory.getLogger(TtsConfiguration.class);

	/** 첫 번째 엔드포인트 기반 Supertone 설정을 만듭니다. */
	@Bean
	public SupertoneConfig supertoneConfig(RagDialogueProperties properties) {
		var supertone = properties.getSupertone();
		if (supertone.getEndpoints().isEmpty()) {
			throw new IllegalStateException("최소 하나 이상의 TTS 엔드포인트를 설정해야 합니다");
		}
		var firstEndpoint = supertone.getEndpoints().get(0);
		return new SupertoneConfig(firstEndpoint.getApiKey(), firstEndpoint.getBaseUrl());
	}

	/** 모든 엔드포인트를 활용하는 TTS 로드 밸런서를 생성합니다. */
	@Bean
	public TtsLoadBalancer ttsLoadBalancer(RagDialogueProperties properties,
		TtsBackpressureMetrics ttsBackpressureMetrics) {
		var supertone = properties.getSupertone();
		List<TtsEndpoint> endpoints = supertone.getEndpoints().stream()
			.map(config -> new TtsEndpoint(config.getId(), config.getApiKey(), config.getBaseUrl()))
			.collect(Collectors.toList());

		TtsLoadBalancer loadBalancer = new TtsLoadBalancer(endpoints);
		loadBalancer.setFailureEventPublisher(event -> {
			ttsBackpressureMetrics.recordEndpointFailure(event.getEndpointId(),
				event.getErrorType(),
				event.getErrorMessage());
			if ("PERMANENT_FAILURE".equals(event.getErrorType())) {
				log.error("TTS 엔드포인트 영구 장애 발생: {}", event);
				return;
			}
			log.warn("TTS 엔드포인트 장애 이벤트: {}", event);
		});

		return loadBalancer;
	}

	/** 로드 밸런서 기반 TTS 포트를 생성합니다. */
	@Bean
	@Primary
	public TtsPort ttsPort(WebClient.Builder webClientBuilder,
		TtsLoadBalancer loadBalancer,
		Voice voice,
		TtsBackpressureMetrics ttsBackpressureMetrics) {
		return new LoadBalancedSupertoneTtsAdapter(webClientBuilder, loadBalancer, voice,
			ttsBackpressureMetrics);
	}
}
