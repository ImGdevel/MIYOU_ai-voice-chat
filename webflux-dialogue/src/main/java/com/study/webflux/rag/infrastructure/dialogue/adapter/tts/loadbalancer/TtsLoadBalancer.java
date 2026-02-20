package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

	/**
	 * 엔드포인트 선택 (크레딧 기반 로드 밸런싱)
	 *
	 * <p>
	 * 선택 알고리즘:
	 * <ol>
	 * <li>HEALTHY + canAcceptRequest() 필터링 (health + rate limit + circuit breaker)</li>
	 * <li>크레딧이 가장 낮은 엔드포인트 우선 선택</li>
	 * <li>동일 크레딧이면 라운드로빈</li>
	 * <li>사용 가능한 엔드포인트가 없으면 fallback</li>
	 * </ol>
	 *
	 * @return 선택된 엔드포인트
	 * @throws IllegalStateException
	 *             사용 가능한 엔드포인트가 없을 때
	 */
	public TtsEndpoint selectEndpoint() {
		long currentTime = System.nanoTime();
		if (currentTime - lastRecoveryCheckTime > RECOVERY_CHECK_INTERVAL_NANOS) {
			tryRecoverTemporaryFailures();
			lastRecoveryCheckTime = currentTime;
		}

		// 1. 사용 가능한 엔드포인트 필터링 (canAcceptRequest()를 한 번만 호출)
		List<TtsEndpoint> eligible = new ArrayList<>();
		for (TtsEndpoint endpoint : endpoints) {
			if (endpoint.canAcceptRequest()) {
				eligible.add(endpoint);
			}
		}

		if (eligible.isEmpty()) {
			return selectFallbackEndpoint();
		}

		// 2. 크레딧 기반 선택 (가장 낮은 크레딧 우선)
		TtsEndpoint bestEndpoint = null;
		double minCredits = Double.MAX_VALUE;
		int countAtMinCredits = 0;

		for (TtsEndpoint endpoint : eligible) {
			double credits = endpoint.getCredits();
			if (credits < minCredits) {
				minCredits = credits;
				bestEndpoint = endpoint;
				countAtMinCredits = 1;
			} else if (Math.abs(credits - minCredits) < 0.01) { // 부동소수점 오차 고려
				countAtMinCredits++;
			}
		}

		// 3. 동일 크레딧이 1개면 즉시 반환
		if (countAtMinCredits == 1) {
			return bestEndpoint;
		}

		// 4. 동일 크레딧이 여러 개면 라운드로빈
		int targetIndex = (roundRobinIndex.getAndIncrement() & Integer.MAX_VALUE)
			% countAtMinCredits;
		int currentIndex = 0;
		for (TtsEndpoint endpoint : eligible) {
			if (Math.abs(endpoint.getCredits() - minCredits) < 0.01) {
				if (currentIndex == targetIndex) {
					return endpoint;
				}
				currentIndex++;
			}
		}

		return bestEndpoint;
	}

	private TtsEndpoint selectFallbackEndpoint() {
		// TEMPORARY_FAILURE 엔드포인트 우선 선택 (복구 가능성 있음)
		for (TtsEndpoint endpoint : endpoints) {
			if (endpoint.getHealth() == TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE) {
				log.warn("모든 HEALTHY 엔드포인트가 없습니다. TEMPORARY_FAILURE 엔드포인트 {} 사용",
					endpoint.getId());
				return endpoint;
			}
		}

		// 모든 엔드포인트가 PERMANENT_FAILURE인 경우 예외 발생
		log.error("모든 TTS 엔드포인트가 영구 장애 상태입니다.");
		throw new IllegalStateException("사용 가능한 TTS 엔드포인트가 없습니다. 모든 엔드포인트가 영구 장애 상태입니다.");
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
		// 서킷 브레이커에 성공 기록
		endpoint.getCircuitBreaker().recordSuccess();

		if (endpoint.getHealth() == TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE) {
			log.info("엔드포인트 {} 정상 상태로 복구", endpoint.getId());
			endpoint.setHealth(TtsEndpoint.EndpointHealth.HEALTHY);
		}
	}

	public void reportFailure(TtsEndpoint endpoint, Throwable error) {
		TtsEndpoint.FailureType failureType = TtsErrorClassifier.classifyError(error);

		// 서킷 브레이커에 실패 기록
		endpoint.getCircuitBreaker().recordFailure(failureType);

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
		publishFailureEvent(endpoint, "TEMPORARY_FAILURE", description);
	}

	private void handlePermanentFailure(TtsEndpoint endpoint, Throwable error) {
		String description = getErrorDescription(error);
		log.error("엔드포인트 {} 영구 장애: {}", endpoint.getId(), description);
		endpoint.setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);
		publishFailureEvent(endpoint, "PERMANENT_FAILURE", description);
	}

	private void handleClientError(TtsEndpoint endpoint, Throwable error) {
		String description = getErrorDescription(error);
		log.warn("클라이언트 에러 발생 (엔드포인트 {} 상태 유지): {}", endpoint.getId(), description);
		// CLIENT_ERROR는 클라이언트 요청 문제이므로 엔드포인트 상태를 변경하지 않음
		// 다음 정상 요청은 해당 엔드포인트에서 처리 가능
		publishFailureEvent(endpoint, "CLIENT_ERROR", description);
	}

	private void publishFailureEvent(TtsEndpoint endpoint, String errorType, String description) {
		if (failureEventPublisher == null) {
			return;
		}
		TtsEndpointFailureEvent event = new TtsEndpointFailureEvent(endpoint.getId(),
			errorType,
			description);
		failureEventPublisher.accept(event);
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
