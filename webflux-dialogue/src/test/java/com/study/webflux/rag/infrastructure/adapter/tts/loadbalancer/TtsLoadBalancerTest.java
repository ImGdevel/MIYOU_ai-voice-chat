package com.study.webflux.rag.infrastructure.adapter.tts.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TtsLoadBalancerTest {

	private List<TtsEndpoint> endpoints;
	private TtsLoadBalancer loadBalancer;

	@BeforeEach
	void setUp() {
		endpoints = List.of(new TtsEndpoint("endpoint-1", "key-1", "http://localhost:8080"),
			new TtsEndpoint("endpoint-2", "key-2", "http://localhost:8080"),
			new TtsEndpoint("endpoint-3", "key-3", "http://localhost:8080"));
		loadBalancer = new TtsLoadBalancer(new ArrayList<>(endpoints));
	}

	@Test
	@DisplayName("Health-aware: HEALTHY 엔드포인트만 선택")
	void selectOnlyHealthyEndpoints() {
		endpoints.get(0).setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
		endpoints.get(1).setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);

		TtsEndpoint selected = loadBalancer.selectEndpoint();

		assertThat(selected.getId()).isEqualTo("endpoint-3");
		assertThat(selected.isAvailable()).isTrue();
	}

	@Test
	@DisplayName("Least-loaded: 활성 요청 수가 가장 적은 엔드포인트 선택")
	void selectLeastLoadedEndpoint() {
		endpoints.get(0).incrementActiveRequests();
		endpoints.get(0).incrementActiveRequests();
		endpoints.get(1).incrementActiveRequests();

		TtsEndpoint selected = loadBalancer.selectEndpoint();

		assertThat(selected.getId()).isEqualTo("endpoint-3");
		assertThat(selected.getActiveRequests()).isEqualTo(0);
	}

	@Test
	@DisplayName("Round-robin: 동일 부하일 때 순차 분배")
	void roundRobinWhenEqualLoad() {
		List<String> selectedIds = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			selectedIds.add(loadBalancer.selectEndpoint().getId());
		}

		assertThat(selectedIds).containsExactlyInAnyOrder("endpoint-1",
			"endpoint-2",
			"endpoint-3",
			"endpoint-1",
			"endpoint-2",
			"endpoint-3");
	}

	@Test
	@DisplayName("일시적 에러 처리 및 복구")
	void handleTemporaryFailure() {
		TtsEndpoint endpoint = endpoints.get(0);
		Exception error = WebClientResponseException
			.create(429, "Too Many Requests", null, null, null);

		loadBalancer.reportFailure(endpoint, error);

		assertThat(endpoint.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
		assertThat(endpoint.getCircuitOpenedAt()).isNotNull();
	}

	@Test
	@DisplayName("영구 장애 처리 및 이벤트 발행")
	void handlePermanentFailure() {
		TtsEndpoint endpoint = endpoints.get(0);
		Exception error = WebClientResponseException
			.create(402, "Not Enough Credits", null, null, null);
		AtomicInteger eventCount = new AtomicInteger(0);

		loadBalancer.setFailureEventPublisher(event -> {
			eventCount.incrementAndGet();
			assertThat(event.getEndpointId()).isEqualTo("endpoint-1");
			assertThat(event.getErrorType()).isEqualTo("PERMANENT_FAILURE");
		});

		loadBalancer.reportFailure(endpoint, error);

		assertThat(endpoint.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);
		assertThat(eventCount.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("클라이언트 에러 처리")
	void handleClientError() {
		TtsEndpoint endpoint = endpoints.get(0);
		Exception error = WebClientResponseException.create(400, "Bad Request", null, null, null);

		loadBalancer.reportFailure(endpoint, error);

		assertThat(endpoint.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.CLIENT_ERROR);
	}

	@Test
	@DisplayName("성공 시 HEALTHY 상태로 복구")
	void recoverToHealthyOnSuccess() {
		TtsEndpoint endpoint = endpoints.get(0);
		endpoint.setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);

		loadBalancer.reportSuccess(endpoint);

		assertThat(endpoint.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY);
	}

	@Test
	@DisplayName("모든 엔드포인트가 비정상일 때 첫 번째 엔드포인트 반환")
	void selectFirstEndpointWhenAllUnhealthy() {
		endpoints.forEach(e -> e.setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE));

		TtsEndpoint selected = loadBalancer.selectEndpoint();

		assertThat(selected).isEqualTo(endpoints.get(0));
	}
}
