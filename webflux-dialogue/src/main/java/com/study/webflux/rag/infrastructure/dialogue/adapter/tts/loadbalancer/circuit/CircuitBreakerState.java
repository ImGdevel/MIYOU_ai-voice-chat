package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.circuit;

/**
 * 서킷 브레이커 상태
 *
 * <p>
 * 서킷 브레이커는 3가지 상태를 가지며 다음과 같이 전이합니다:
 * <ul>
 * <li>CLOSED → OPEN: 실패 발생 시 (TEMPORARY 또는 PERMANENT)</li>
 * <li>OPEN → HALF_OPEN: 백오프 시간 경과 후</li>
 * <li>HALF_OPEN → CLOSED: 요청 성공 시</li>
 * <li>HALF_OPEN → OPEN: 요청 실패 시 (백오프 시간 증가)</li>
 * </ul>
 */
public enum CircuitBreakerState {
	/**
	 * 정상 상태 - 모든 요청 허용
	 */
	CLOSED,

	/**
	 * 차단 상태 - 요청 차단, 백오프 시간 경과 후 HALF_OPEN으로 전이
	 */
	OPEN,

	/**
	 * 복구 테스트 상태 - 제한된 요청 허용, 성공 시 CLOSED로 전이
	 */
	HALF_OPEN
}
