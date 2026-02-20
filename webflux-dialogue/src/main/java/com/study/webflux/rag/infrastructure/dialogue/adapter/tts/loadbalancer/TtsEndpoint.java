package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.EndpointCircuitBreaker;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.ExponentialBackoffStrategy;

/**
 * TTS 엔드포인트
 */
public class TtsEndpoint {
	private final String id;
	private final String apiKey;
	private final String baseUrl;
	private final AtomicInteger activeRequests;
	private final int maxConcurrentRequests;
	private final Object healthLock = new Object();
	private final EndpointCircuitBreaker circuitBreaker;
	private volatile EndpointHealth health;
	private volatile Instant circuitOpenedAt;
	private volatile double credits = Double.MAX_VALUE;

	public TtsEndpoint(String id, String apiKey, String baseUrl) {
		this(id, apiKey, baseUrl, 10);
	}

	public TtsEndpoint(String id, String apiKey, String baseUrl, int maxConcurrentRequests) {
		this(id, apiKey, baseUrl, maxConcurrentRequests, new ExponentialBackoffStrategy(
			Duration.ofSeconds(5),
			Duration.ofSeconds(300)));
	}

	public TtsEndpoint(String id,
		String apiKey,
		String baseUrl,
		int maxConcurrentRequests,
		ExponentialBackoffStrategy backoffStrategy) {
		this.id = id;
		this.apiKey = apiKey;
		this.baseUrl = baseUrl;
		this.maxConcurrentRequests = maxConcurrentRequests;
		this.health = EndpointHealth.HEALTHY;
		this.activeRequests = new AtomicInteger(0);
		this.circuitOpenedAt = null;
		this.circuitBreaker = new EndpointCircuitBreaker(backoffStrategy);
	}

	public String getId() {
		return id;
	}

	public String getApiKey() {
		return apiKey;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public EndpointHealth getHealth() {
		return health;
	}

	public void setHealth(EndpointHealth health) {
		synchronized (healthLock) {
			this.health = health;
			if (health == EndpointHealth.TEMPORARY_FAILURE
				|| health == EndpointHealth.PERMANENT_FAILURE) {
				this.circuitOpenedAt = Instant.now();
			} else if (health == EndpointHealth.HEALTHY) {
				this.circuitOpenedAt = null;
			}
		}
	}

	public int getActiveRequests() {
		return activeRequests.get();
	}

	public int incrementActiveRequests() {
		return activeRequests.incrementAndGet();
	}

	public int decrementActiveRequests() {
		return activeRequests.decrementAndGet();
	}

	public Instant getCircuitOpenedAt() {
		return circuitOpenedAt;
	}

	public EndpointCircuitBreaker getCircuitBreaker() {
		return circuitBreaker;
	}

	public double getCredits() {
		return credits;
	}

	public void updateCredits(double credits) {
		this.credits = credits;
	}

	public boolean isAvailable() {
		return health == EndpointHealth.HEALTHY;
	}

	/**
	 * 요청 수락 가능 여부 확인
	 *
	 * <p>
	 * 다음 조건을 모두 만족해야 요청 수락 가능:
	 * <ul>
	 * <li>엔드포인트 상태가 HEALTHY</li>
	 * <li>동시 요청 수가 최대값 미만</li>
	 * <li>서킷 브레이커가 요청 허용 상태</li>
	 * </ul>
	 *
	 * @return 요청 수락 가능 여부
	 */
	public boolean canAcceptRequest() {
		return health == EndpointHealth.HEALTHY
			&& activeRequests.get() < maxConcurrentRequests
			&& circuitBreaker.allowRequest();
	}

	/**
	 * 엔드포인트 상태
	 */
	public enum EndpointHealth {
		HEALTHY,
		TEMPORARY_FAILURE,
		PERMANENT_FAILURE,
		CLIENT_ERROR
	}

	/**
	 * 실패 유형
	 */
	public enum FailureType {
		TEMPORARY,
		PERMANENT,
		CLIENT_ERROR
	}
}
