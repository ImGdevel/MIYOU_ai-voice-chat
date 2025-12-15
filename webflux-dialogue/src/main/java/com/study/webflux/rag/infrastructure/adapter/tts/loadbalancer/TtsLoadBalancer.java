package com.study.webflux.rag.infrastructure.adapter.tts.loadbalancer;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TTS 엔드포인트 로드 밸런서
 *
 * 아래와 같은 전략을 사용하여 TTS 엔드포인트를 선택합니다.
 * Health-aware: Circuit breaker 상태의 endpoint 자동 제외 및 복구
 * Least-loaded: 활성 요청 수가 가장 적은 endpoint 우선 선택
 * Round-robin: 동일 부하일 때 순차 분배
 */
public class TtsLoadBalancer {
	private static final Logger log = LoggerFactory.getLogger(TtsLoadBalancer.class);
	private static final Duration CIRCUIT_BREAKER_TIMEOUT = Duration.ofMinutes(5);
	private static final long CIRCUIT_BREAKER_CHECK_INTERVAL_NANOS = Duration.ofSeconds(10).toNanos();

	private final List<TtsEndpoint> endpoints;
	private final AtomicInteger roundRobinIndex;
	private volatile long lastCircuitBreakerCheckTime;

	public TtsLoadBalancer(List<TtsEndpoint> endpoints) {
		if (endpoints == null || endpoints.isEmpty()) {
			throw new IllegalArgumentException("하나 이상의 TTS 엔드포인트가 필요합니다.");
		}
		this.endpoints = endpoints;
		this.roundRobinIndex = new AtomicInteger(0);
		this.lastCircuitBreakerCheckTime = System.nanoTime();
	}

	public TtsEndpoint selectEndpoint() {
		long currentTime = System.nanoTime();
		if (currentTime - lastCircuitBreakerCheckTime > CIRCUIT_BREAKER_CHECK_INTERVAL_NANOS) {
			tryRecoverCircuitBreakers();
			lastCircuitBreakerCheckTime = currentTime;
		}

		TtsEndpoint bestEndpoint = null;
		int minLoad = Integer.MAX_VALUE;
		int countAtMinLoad = 0;

		for (TtsEndpoint endpoint : endpoints) {
			if (!endpoint.isAvailable()) {
				continue;
			}

			int load = endpoint.getActiveRequests();
			if (load < minLoad) {
				minLoad = load;
				bestEndpoint = endpoint;
				countAtMinLoad = 1;
			} else if (load == minLoad) {
				countAtMinLoad++;
			}
		}

		if (bestEndpoint == null) {
			log.warn("모든 TTS 엔드포인트가 비정상 상태입니다. 기본 엔드포인트를 사용합니다.");
			return endpoints.get(0);
		}

		if (countAtMinLoad == 1) {
			return bestEndpoint;
		}

		int targetIndex = roundRobinIndex.getAndIncrement() % countAtMinLoad;
		int currentIndex = 0;
		for (TtsEndpoint endpoint : endpoints) {
			if (endpoint.isAvailable() && endpoint.getActiveRequests() == minLoad) {
				if (currentIndex == targetIndex) {
					return endpoint;
				}
				currentIndex++;
			}
		}

		return bestEndpoint;
	}

	private void tryRecoverCircuitBreakers() {
		Instant now = Instant.now();
		for (TtsEndpoint endpoint : endpoints) {
			if (endpoint.getHealth() == TtsEndpoint.EndpointHealth.CIRCUIT_OPEN
				&& endpoint.getCircuitOpenedAt() != null
				&& Duration.between(endpoint.getCircuitOpenedAt(), now).compareTo(CIRCUIT_BREAKER_TIMEOUT) > 0) {
				log.info("엔드포인트 {} 서킷 브레이커 복구", endpoint.getId());
				endpoint.setHealth(TtsEndpoint.EndpointHealth.HEALTHY);
			}
		}
	}

	public void reportSuccess(TtsEndpoint endpoint) {
		if (endpoint.getHealth() != TtsEndpoint.EndpointHealth.HEALTHY) {
			log.info("엔드포인트 {} 정상 상태로 복구", endpoint.getId());
			endpoint.setHealth(TtsEndpoint.EndpointHealth.HEALTHY);
		}
	}

	public void reportFailure(TtsEndpoint endpoint, Throwable error) {
		String errorMessage = error.getMessage();

		if (errorMessage != null) {
			if (errorMessage.contains("429") || errorMessage.contains("rate limit")) {
				log.warn("엔드포인트 {} 요청 제한 상태", endpoint.getId());
				endpoint.setHealth(TtsEndpoint.EndpointHealth.RATE_LIMITED);
			} else if (errorMessage.contains("quota") || errorMessage.contains("insufficient")) {
				log.warn("엔드포인트 {} 할당량 초과", endpoint.getId());
				endpoint.setHealth(TtsEndpoint.EndpointHealth.QUOTA_EXCEEDED);
			} else {
				log.error("엔드포인트 {} 장애 발생, 서킷 브레이커 활성화", endpoint.getId(), error);
				endpoint.setHealth(TtsEndpoint.EndpointHealth.CIRCUIT_OPEN);
			}
		} else {
			log.error("엔드포인트 {} 알 수 없는 오류 발생, 서킷 브레이커 활성화", endpoint.getId(), error);
			endpoint.setHealth(TtsEndpoint.EndpointHealth.CIRCUIT_OPEN);
		}
	}

	public List<TtsEndpoint> getEndpoints() {
		return endpoints;
	}
}
