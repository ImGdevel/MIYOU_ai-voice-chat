package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.circuit;

import java.time.Duration;

/**
 * 지수 백오프 전략
 *
 * <p>
 * 실패 횟수에 따라 백오프 시간을 지수적으로 증가시킵니다. 공식: {@code min(baseDelay * 2^(failureCount - 1), maxDelay)}
 *
 * <p>
 * 예시 (baseDelay=5초, maxDelay=300초):
 * <ul>
 * <li>1회 실패: 5초</li>
 * <li>2회 실패: 10초</li>
 * <li>3회 실패: 20초</li>
 * <li>4회 실패: 40초</li>
 * <li>5회 실패: 80초</li>
 * <li>6회 실패: 160초</li>
 * <li>7회 이상: 300초 (max cap)</li>
 * </ul>
 */
public class ExponentialBackoffStrategy {
	private final Duration baseDelay;
	private final Duration maxDelay;

	public ExponentialBackoffStrategy(Duration baseDelay, Duration maxDelay) {
		if (baseDelay.isNegative() || baseDelay.isZero()) {
			throw new IllegalArgumentException("baseDelay must be positive");
		}
		if (maxDelay.compareTo(baseDelay) < 0) {
			throw new IllegalArgumentException(
				"maxDelay must be greater than or equal to baseDelay");
		}
		this.baseDelay = baseDelay;
		this.maxDelay = maxDelay;
	}

	/**
	 * 실패 횟수에 따른 백오프 시간 계산
	 *
	 * @param failureCount
	 *            실패 횟수 (0 이상)
	 * @return 백오프 지연 시간
	 */
	public Duration calculateDelay(int failureCount) {
		if (failureCount <= 0) {
			return Duration.ZERO;
		}

		// 2^(failureCount - 1) 계산
		long multiplier = (long) Math.pow(2, failureCount - 1);
		long delaySeconds = baseDelay.toSeconds() * multiplier;

		// maxDelay로 제한
		return Duration.ofSeconds(Math.min(delaySeconds, maxDelay.toSeconds()));
	}

	public Duration getBaseDelay() {
		return baseDelay;
	}

	public Duration getMaxDelay() {
		return maxDelay;
	}
}
