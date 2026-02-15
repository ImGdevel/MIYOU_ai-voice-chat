package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	@DisplayName("클라이언트 에러 발생 시 엔드포인트 상태 유지")
	void handleClientError_keepsEndpointHealthy() {
		TtsEndpoint endpoint = endpoints.get(0);
		Exception error = WebClientResponseException.create(400, "Bad Request", null, null, null);

		loadBalancer.reportFailure(endpoint, error);

		// CLIENT_ERROR는 클라이언트 요청 문제이므로 엔드포인트 상태 유지
		assertThat(endpoint.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY);
		assertThat(endpoint.isAvailable()).isTrue();
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

	// ==================== 취약점 수정 검증 테스트 ====================

	@Test
	@DisplayName("[수정됨] 모든 엔드포인트가 PERMANENT_FAILURE일 때 예외 발생")
	void allPermanentFailure_throwsException() {
		// Given: 모든 엔드포인트가 PERMANENT_FAILURE 상태
		endpoints.forEach(e -> e.setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE));

		// When & Then: 예외 발생
		assertThatThrownBy(() -> loadBalancer.selectEndpoint())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("사용 가능한 TTS 엔드포인트가 없습니다");
	}

	@Test
	@DisplayName("[수정됨] HEALTHY 없을 때 TEMPORARY_FAILURE 엔드포인트 우선 선택")
	void noHealthy_selectsTemporaryFailureFirst() {
		// Given: 일부 TEMPORARY_FAILURE, 일부 PERMANENT_FAILURE
		endpoints.get(0).setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);
		endpoints.get(1).setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
		endpoints.get(2).setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);

		// When
		TtsEndpoint selected = loadBalancer.selectEndpoint();

		// Then: TEMPORARY_FAILURE 엔드포인트 선택 (복구 가능성 있음)
		assertThat(selected.getId()).isEqualTo("endpoint-2");
		assertThat(selected.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
	}

	@Test
	@DisplayName("[수정됨] CLIENT_ERROR 발생 후에도 해당 엔드포인트가 정상 선택됨")
	void clientError_endpointStillSelected() {
		// Given: endpoint-1에서 400 Bad Request 발생
		TtsEndpoint endpoint1 = endpoints.get(0);
		Exception badRequest = WebClientResponseException
			.create(400, "Bad Request", null, null, null);
		loadBalancer.reportFailure(endpoint1, badRequest);

		// Then: endpoint-1이 여전히 HEALTHY 상태
		assertThat(endpoint1.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY);
		assertThat(endpoint1.isAvailable()).isTrue();

		// When: 다음 요청에서 엔드포인트 선택
		List<String> selectedIds = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			selectedIds.add(loadBalancer.selectEndpoint().getId());
		}

		// Then: endpoint-1도 정상적으로 선택됨
		assertThat(selectedIds).contains("endpoint-1");
	}
}
