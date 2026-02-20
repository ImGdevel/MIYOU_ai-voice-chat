package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer;

import java.time.Instant;

/**
 * TTS 엔드포인트 크레딧 부족 이벤트
 *
 * <p>
 * 엔드포인트의 남은 크레딧이 임계값 미만으로 떨어졌을 때 발행됩니다.
 */
public class TtsLowCreditEvent {
	private final String endpointId;
	private final double remainingCredits;
	private final double threshold;
	private final Instant occurredAt;

	public TtsLowCreditEvent(String endpointId, double remainingCredits, double threshold) {
		this.endpointId = endpointId;
		this.remainingCredits = remainingCredits;
		this.threshold = threshold;
		this.occurredAt = Instant.now();
	}

	public String getEndpointId() {
		return endpointId;
	}

	public double getRemainingCredits() {
		return remainingCredits;
	}

	public double getThreshold() {
		return threshold;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	@Override
	public String toString() {
		return "TtsLowCreditEvent{"
			+ "endpointId='" + endpointId + '\''
			+ ", remainingCredits=" + remainingCredits
			+ ", threshold=" + threshold
			+ ", occurredAt=" + occurredAt
			+ '}';
	}
}
