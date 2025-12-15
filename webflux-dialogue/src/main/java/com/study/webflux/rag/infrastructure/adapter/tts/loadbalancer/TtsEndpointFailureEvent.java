package com.study.webflux.rag.infrastructure.adapter.tts.loadbalancer;

import java.time.Instant;

/**
 * TTS 엔드포인트 영구 장애 이벤트
 */
public class TtsEndpointFailureEvent {
	private final String endpointId;
	private final String errorType;
	private final String errorMessage;
	private final Instant occurredAt;

	public TtsEndpointFailureEvent(String endpointId, String errorType, String errorMessage) {
		this.endpointId = endpointId;
		this.errorType = errorType;
		this.errorMessage = errorMessage;
		this.occurredAt = Instant.now();
	}

	public String getEndpointId() {
		return endpointId;
	}

	public String getErrorType() {
		return errorType;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	@Override
	public String toString() {
		return String.format("TtsEndpointFailureEvent{endpointId='%s', errorType='%s', errorMessage='%s', occurredAt=%s}",
			endpointId, errorType, errorMessage, occurredAt);
	}
}
