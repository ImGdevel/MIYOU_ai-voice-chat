package com.study.webflux.rag.infrastructure.monitoring.config;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer;
import jakarta.annotation.PostConstruct;

/**
 * 애플리케이션 시작 시 TTS 엔드포인트 메트릭 Gauge를 등록합니다.
 */
@Component
public class TtsEndpointMetricsRegistrar {

	private final TtsLoadBalancer ttsLoadBalancer;
	private final TtsBackpressureMetrics ttsBackpressureMetrics;

	public TtsEndpointMetricsRegistrar(TtsLoadBalancer ttsLoadBalancer,
		TtsBackpressureMetrics ttsBackpressureMetrics) {
		this.ttsLoadBalancer = ttsLoadBalancer;
		this.ttsBackpressureMetrics = ttsBackpressureMetrics;
	}

	@PostConstruct
	public void register() {
		ttsBackpressureMetrics.registerEndpoints(ttsLoadBalancer.getEndpoints());
	}
}
