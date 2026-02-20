package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.circuit;

import java.time.Duration;
import java.time.Instant;

import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint;

/**
 * 엔드포인트별 서킷 브레이커
 *
 * <p>
 * TTS 엔드포인트의 장애를 감지하고 복구를 관리합니다.
 *
 * <h3>상태 전이</h3>
 * <ul>
 * <li>CLOSED → OPEN: TEMPORARY 또는 PERMANENT 실패 발생</li>
 * <li>OPEN → HALF_OPEN: 백오프 시간 경과 후 자동 전이</li>
 * <li>HALF_OPEN → CLOSED: 요청 성공 시</li>
 * <li>HALF_OPEN → OPEN: 요청 실패 시 (실패 카운트 증가)</li>
 * </ul>
 *
 * <h3>장애 타입별 처리</h3>
 * <ul>
 * <li>PERMANENT (401/403): 즉시 OPEN, 복구 불가 (failureCount = MAX)</li>
 * <li>TEMPORARY (429/500): OPEN 후 지수 백오프로 복구 시도</li>
 * <li>CLIENT_ERROR (400): 상태 변경 없음</li>
 * </ul>
 */
public class EndpointCircuitBreaker {
	private volatile CircuitBreakerState state;
	private volatile Instant circuitOpenedAt;
	private volatile int failureCount;
	private final ExponentialBackoffStrategy backoffStrategy;
	private final Object stateLock = new Object();

	public EndpointCircuitBreaker(ExponentialBackoffStrategy backoffStrategy) {
		this.state = CircuitBreakerState.CLOSED;
		this.backoffStrategy = backoffStrategy;
		this.failureCount = 0;
	}

	/**
	 * 요청 허용 여부 확인
	 *
	 * <p>
	 * CLOSED: 허용
	 * <p>
	 * HALF_OPEN: 허용 (호출자가 요청 수 제한)
	 * <p>
	 * OPEN: 백오프 시간 경과 시 HALF_OPEN으로 전이 후 허용, 그렇지 않으면 차단
	 *
	 * @return 요청 허용 여부
	 */
	public boolean allowRequest() {
		CircuitBreakerState currentState = this.state;

		if (currentState == CircuitBreakerState.CLOSED) {
			return true;
		}

		if (currentState == CircuitBreakerState.HALF_OPEN) {
			return true;
		}

		// OPEN 상태: 백오프 시간 확인
		if (currentState == CircuitBreakerState.OPEN) {
			// PERMANENT 장애는 복구 불가
			if (failureCount == Integer.MAX_VALUE) {
				return false;
			}
			Duration backoffDuration = backoffStrategy.calculateDelay(failureCount);
			Instant openedAt = this.circuitOpenedAt;

			if (openedAt != null
				&& Duration.between(openedAt, Instant.now()).compareTo(backoffDuration) > 0) {
				transitionToHalfOpen();
				return true;
			}
			return false;
		}

		return false;
	}

	/**
	 * 요청 성공 기록
	 *
	 * <p>
	 * HALF_OPEN 상태에서 성공 시 CLOSED로 전이하고 실패 카운트 초기화
	 */
	public void recordSuccess() {
		synchronized (stateLock) {
			if (state == CircuitBreakerState.HALF_OPEN) {
				state = CircuitBreakerState.CLOSED;
				failureCount = 0;
				circuitOpenedAt = null;
			}
		}
	}

	/**
	 * 요청 실패 기록
	 *
	 * <p>
	 * 장애 타입에 따라 상태 전이:
	 * <ul>
	 * <li>PERMANENT: OPEN으로 전이, 복구 불가 (failureCount = MAX)</li>
	 * <li>TEMPORARY: OPEN으로 전이, 실패 카운트 증가 (최대 10)</li>
	 * <li>CLIENT_ERROR: 상태 변경 없음</li>
	 * </ul>
	 *
	 * @param failureType
	 *            장애 타입
	 */
	public void recordFailure(TtsEndpoint.FailureType failureType) {
		synchronized (stateLock) {
			if (failureType == TtsEndpoint.FailureType.PERMANENT) {
				// 401/403 등 영구 장애: 복구 불가
				state = CircuitBreakerState.OPEN;
				circuitOpenedAt = Instant.now();
				failureCount = Integer.MAX_VALUE;
			} else if (failureType == TtsEndpoint.FailureType.TEMPORARY) {
				// 429/500 등 일시적 장애: 지수 백오프로 복구 시도
				state = CircuitBreakerState.OPEN;
				circuitOpenedAt = Instant.now();
				failureCount = Math.min(failureCount + 1, 10); // 최대 10으로 제한
			}
			// CLIENT_ERROR는 엔드포인트 문제가 아니므로 상태 변경 없음
		}
	}

	/**
	 * HALF_OPEN 상태로 전이
	 */
	private void transitionToHalfOpen() {
		synchronized (stateLock) {
			if (state == CircuitBreakerState.OPEN) {
				state = CircuitBreakerState.HALF_OPEN;
			}
		}
	}

	/**
	 * 서킷 브레이커 상태 조회
	 *
	 * @return 현재 상태
	 */
	public CircuitBreakerState getState() {
		return state;
	}

	/**
	 * 실패 횟수 조회
	 *
	 * @return 누적 실패 횟수
	 */
	public int getFailureCount() {
		return failureCount;
	}

	/**
	 * 서킷 오픈 시각 조회
	 *
	 * @return 서킷이 열린 시각 (null이면 CLOSED 상태)
	 */
	public Instant getCircuitOpenedAt() {
		return circuitOpenedAt;
	}
}
