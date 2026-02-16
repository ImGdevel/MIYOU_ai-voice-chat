package com.study.webflux.rag.infrastructure.monitoring.config;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpointFailureEvent;

/**
 * TTS 장애 이벤트를 수신하여 모니터링 메트릭으로 기록합니다.
 */
@Component
public class TtsMonitoringEventListener {

	private final TtsBackpressureMetrics ttsBackpressureMetrics;

	public TtsMonitoringEventListener(TtsBackpressureMetrics ttsBackpressureMetrics) {
		this.ttsBackpressureMetrics = ttsBackpressureMetrics;
	}

	@EventListener
	public void handleFailureEvent(TtsEndpointFailureEvent event) {
		ttsBackpressureMetrics.recordEndpointFailure(event.getEndpointId(),
			event.getErrorType(),
			event.getErrorMessage());
	}
}
