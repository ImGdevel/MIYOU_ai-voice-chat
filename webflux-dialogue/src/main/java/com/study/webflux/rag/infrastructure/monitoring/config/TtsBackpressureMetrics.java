package com.study.webflux.rag.infrastructure.monitoring.config;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * TTS Backpressure 메트릭을 추적합니다.
 *
 * <p>
 * Phase 1A: Pipeline Bottleneck Analysis - TTS Endpoint Queue 크기 - Endpoint별 활성 요청 수
 */
@Component
public class TtsBackpressureMetrics {

	private final MeterRegistry meterRegistry;
	private final ConcurrentHashMap<String, TtsEndpoint> registeredEndpoints = new ConcurrentHashMap<>();

	public TtsBackpressureMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	/**
	 * TTS 엔드포인트를 등록하고 메트릭 Gauge를 생성합니다.
	 */
	public void registerEndpoints(List<TtsEndpoint> endpoints) {
		for (TtsEndpoint endpoint : endpoints) {
			if (!registeredEndpoints.containsKey(endpoint.getId())) {
				registeredEndpoints.put(endpoint.getId(), endpoint);

				// Queue size (active requests) gauge
				Gauge.builder("tts.endpoint.queue.size", endpoint, TtsEndpoint::getActiveRequests)
					.tag("endpoint", endpoint.getId())
					.description("Number of active requests for TTS endpoint")
					.register(meterRegistry);

				// Health status gauge (0=HEALTHY, 1=TEMPORARY_FAILURE, 2=PERMANENT_FAILURE, 3=CLIENT_ERROR)
				Gauge.builder("tts.endpoint.health", endpoint, e -> {
					switch (e.getHealth()) {
						case HEALTHY :
							return 0;
						case TEMPORARY_FAILURE :
							return 1;
						case PERMANENT_FAILURE :
							return 2;
						case CLIENT_ERROR :
							return 3;
						default :
							return -1;
					}
				})
					.tag("endpoint", endpoint.getId())
					.description("TTS endpoint health status")
					.register(meterRegistry);
			}
		}
	}

	/**
	 * 엔드포인트 목록을 업데이트합니다.
	 */
	public void updateEndpoints(List<TtsEndpoint> endpoints) {
		registerEndpoints(endpoints);
	}
}
