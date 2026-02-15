package com.study.webflux.rag.infrastructure.monitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * TTS 로드밸런서 메트릭을 Prometheus에 노출합니다.
 */
@Configuration
public class TtsMetricsConfiguration {

	@Bean
	public MeterBinder ttsEndpointMetrics(TtsLoadBalancer loadBalancer) {
		return registry -> {
			for (TtsEndpoint endpoint : loadBalancer.getEndpoints()) {
				Gauge
					.builder("tts.endpoint.active.requests",
						endpoint,
						TtsEndpoint::getActiveRequests)
					.description("TTS 엔드포인트별 활성 요청 수")
					.tag("endpoint", endpoint.getId())
					.register(registry);

				Gauge.builder("tts.endpoint.health", endpoint, ep -> healthToNumber(ep.getHealth()))
					.description("TTS 엔드포인트 상태 (1=HEALTHY, 0=TEMP_FAIL, -1=PERM_FAIL)")
					.tag("endpoint", endpoint.getId())
					.register(registry);
			}
		};
	}

	private double healthToNumber(TtsEndpoint.EndpointHealth health) {
		return switch (health) {
			case HEALTHY -> 1;
			case TEMPORARY_FAILURE -> 0;
			case PERMANENT_FAILURE -> -1;
			case CLIENT_ERROR -> 0;
		};
	}
}
