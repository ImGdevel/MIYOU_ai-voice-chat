package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TTS 엔드포인트 로드 밸런서
 *
 * 아래와 같은 전략을 사용하여 TTS 엔드포인트를 선택합니다. Health-aware: Circuit breaker 상태의 endpoint 자동 제외 및 복구 Least-loaded: 활성 요청 수가 가장 적은
 * endpoint 우선 선택 Round-robin: 동일 부하일 때 순차 분배
 */
public class TtsLoadBalancer {
	private static final Logger log = LoggerFactory.getLogger(TtsLoadBalancer.class);
	private static final Duration TEMPORARY_FAILURE_RECOVERY_INTERVAL = Duration.ofSeconds(30);
	private static final long RECOVERY_CHECK_INTERVAL_NANOS = Duration.ofSeconds(10).toNanos();

	private final List<TtsEndpoint> endpoints;
	private final AtomicInteger roundRobinIndex;
	private volatile long lastRecoveryCheckTime;
	private volatile Consumer<TtsEndpointFailureEvent> failureEventPublisher;

	public TtsLoadBalancer(List<TtsEndpoint> endpoints) {
		if (endpoints == null || endpoints.isEmpty()) {
			throw new IllegalArgumentException("하나 이상의 TTS 엔드포인트가 필요합니다.");
		}
		this.endpoints = endpoints;
		this.roundRobinIndex = new AtomicInteger(0);
		this.lastRecoveryCheckTime = System.nanoTime();
	}

	public void setFailureEventPublisher(Consumer<TtsEndpointFailureEvent> publisher) {
		this.failureEventPublisher = publisher;
	}

	public TtsEndpoint selectEndpoint() {
		long currentTime = System.nanoTime();
		if (currentTime - lastRecoveryCheckTime > RECOVERY_CHECK_INTERVAL_NANOS) {
			tryRecoverTemporaryFailures();
			lastRecoveryCheckTime = currentTime;
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

		int targetIndex = (roundRobinIndex.getAndIncrement() & Integer.MAX_VALUE) % countAtMinLoad;
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

	private void tryRecoverTemporaryFailures() {
		Instant now = Instant.now();
		for (TtsEndpoint endpoint : endpoints) {
			if (endpoint.getHealth() == TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE
				&& endpoint.getCircuitOpenedAt() != null
				&& Duration.between(endpoint.getCircuitOpenedAt(), now)
					.compareTo(TEMPORARY_FAILURE_RECOVERY_INTERVAL) > 0) {
				log.info("엔드포인트 {} 일시적 장애 복구 시도", endpoint.getId());
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
		TtsEndpoint.FailureType failureType = TtsErrorClassifier.classifyError(error);

		switch (failureType) {
			case TEMPORARY -> handleTemporaryFailure(endpoint, error);
			case PERMANENT -> handlePermanentFailure(endpoint, error);
			case CLIENT_ERROR -> handleClientError(endpoint, error);
		}
	}

	private void handleTemporaryFailure(TtsEndpoint endpoint, Throwable error) {
		String description = getErrorDescription(error);
		log.warn("엔드포인트 {} 일시적 장애: {}", endpoint.getId(), description);
		endpoint.setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
	}

	private void handlePermanentFailure(TtsEndpoint endpoint, Throwable error) {
		String description = getErrorDescription(error);
		log.error("엔드포인트 {} 영구 장애: {}", endpoint.getId(), description);
		endpoint.setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);

		if (failureEventPublisher != null) {
			TtsEndpointFailureEvent event = new TtsEndpointFailureEvent(endpoint.getId(),
				"PERMANENT_FAILURE", description);
			failureEventPublisher.accept(event);
		}
	}

	private void handleClientError(TtsEndpoint endpoint, Throwable error) {
		String description = getErrorDescription(error);
		log.warn("엔드포인트 {} 클라이언트 에러: {}", endpoint.getId(), description);
		endpoint.setHealth(TtsEndpoint.EndpointHealth.CLIENT_ERROR);
	}

	private String getErrorDescription(Throwable error) {
		if (error instanceof WebClientResponseException webClientError) {
			int statusCode = webClientError.getStatusCode().value();
			return String
				.format("[%d] %s", statusCode, TtsErrorClassifier.getErrorDescription(statusCode));
		}
		return error.getMessage() != null ? error.getMessage() : "알 수 없는 오류";
	}

	public List<TtsEndpoint> getEndpoints() {
		return endpoints;
	}
}
