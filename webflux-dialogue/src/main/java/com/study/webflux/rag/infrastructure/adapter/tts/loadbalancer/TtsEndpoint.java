package com.study.webflux.rag.infrastructure.adapter.tts.loadbalancer;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TTS 엔드포인트
 */
public class TtsEndpoint {
	private final String id;
	private final String apiKey;
	private final String baseUrl;
	private volatile EndpointHealth health;
	private final AtomicInteger activeRequests;
	private volatile Instant circuitOpenedAt;

	public TtsEndpoint(String id, String apiKey, String baseUrl) {
		this.id = id;
		this.apiKey = apiKey;
		this.baseUrl = baseUrl;
		this.health = EndpointHealth.HEALTHY;
		this.activeRequests = new AtomicInteger(0);
		this.circuitOpenedAt = null;
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
		this.health = health;
		if (health == EndpointHealth.CIRCUIT_OPEN) {
			this.circuitOpenedAt = Instant.now();
		} else if (health == EndpointHealth.HEALTHY) {
			this.circuitOpenedAt = null;
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

	public boolean isAvailable() {
		return health == EndpointHealth.HEALTHY;
	}

	/**
	 * 엔드포인트 상태
	 */
	public enum EndpointHealth {
		HEALTHY,
		RATE_LIMITED,
		CIRCUIT_OPEN,
		QUOTA_EXCEEDED
	}
}
