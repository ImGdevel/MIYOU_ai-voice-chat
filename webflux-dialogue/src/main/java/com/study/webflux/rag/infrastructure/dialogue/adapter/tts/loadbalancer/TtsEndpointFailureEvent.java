package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer;

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
		if (endpointId == null || endpointId.isBlank()) {
			throw new IllegalArgumentException("엔드포인트 ID는 필수입니다");
		}
		if (errorType == null || errorType.isBlank()) {
			throw new IllegalArgumentException("에러 타입은 필수입니다");
		}
		if (errorMessage == null) {
			throw new IllegalArgumentException("에러 메시지는 필수입니다");
		}
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
		return String.format(
			"TtsEndpointFailureEvent{endpointId='%s', errorType='%s', errorMessage='%s', occurredAt=%s}",
			endpointId,
			errorType,
			errorMessage,
			occurredAt);
	}
}
