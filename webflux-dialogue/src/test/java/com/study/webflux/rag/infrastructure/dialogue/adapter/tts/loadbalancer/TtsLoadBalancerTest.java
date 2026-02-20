package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.CircuitBreakerState;
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

		// 기본 크레딧을 동일하게 설정 (라운드로빈 동작 보장)
		endpoints.forEach(e -> e.updateCredits(100.0));
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
	@DisplayName("크레딧 기반: 크레딧이 가장 적은 엔드포인트 우선 선택")
	void selectLowestCreditEndpoint() {
		// 크레딧 설정: endpoint-1=100, endpoint-2=50, endpoint-3=200
		endpoints.get(0).updateCredits(100.0);
		endpoints.get(1).updateCredits(50.0);
		endpoints.get(2).updateCredits(200.0);

		TtsEndpoint selected = loadBalancer.selectEndpoint();

		// 크레딧이 가장 적은 endpoint-2가 선택되어야 함
		assertThat(selected.getId()).isEqualTo("endpoint-2");
		assertThat(selected.getCredits()).isEqualTo(50.0);
	}

	@Test
	@DisplayName("Round-robin: 동일 크레딧일 때 순차 분배")
	void roundRobinWhenEqualCredits() {
		// 모든 엔드포인트 동일 크레딧 설정
		endpoints.get(0).updateCredits(100.0);
		endpoints.get(1).updateCredits(100.0);
		endpoints.get(2).updateCredits(100.0);

		List<String> selectedIds = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			selectedIds.add(loadBalancer.selectEndpoint().getId());
		}

		// 동일 크레딧이므로 라운드로빈으로 순차 분배
		assertThat(selectedIds).containsExactly("endpoint-1",
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
		// Given: 모든 엔드포인트 동일 크레딧 설정
		endpoints.forEach(e -> e.updateCredits(100.0));

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

		// Then: endpoint-1도 정상적으로 선택됨 (circuit breaker 상태 변경 없음)
		assertThat(selectedIds).contains("endpoint-1");
	}

	// ==================== 동시성 테스트 ====================

	@Test
	@DisplayName("[동시성] 여러 스레드에서 health 상태 동시 변경 시 데이터 무결성 유지")
	void concurrentHealthStateChanges_maintainsDataIntegrity() throws InterruptedException {
		// Given
		TtsEndpoint endpoint = endpoints.get(0);
		int threadCount = 50;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);

		// When: 50개 스레드에서 동시에 health 상태 변경
		for (int i = 0; i < threadCount; i++) {
			final int index = i;
			executor.submit(() -> {
				try {
					if (index % 3 == 0) {
						endpoint.setHealth(TtsEndpoint.EndpointHealth.HEALTHY);
					} else if (index % 3 == 1) {
						endpoint.setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
					} else {
						endpoint.setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);
					}
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(5, TimeUnit.SECONDS);
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.SECONDS);

		// Then: health와 circuitOpenedAt의 일관성 확인
		TtsEndpoint.EndpointHealth finalHealth = endpoint.getHealth();
		if (finalHealth == TtsEndpoint.EndpointHealth.HEALTHY) {
			assertThat(endpoint.getCircuitOpenedAt()).isNull();
		} else if (finalHealth == TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE
			|| finalHealth == TtsEndpoint.EndpointHealth.PERMANENT_FAILURE) {
			assertThat(endpoint.getCircuitOpenedAt()).isNotNull();
		}
	}

	@Test
	@DisplayName("[동시성] 여러 스레드에서 동시에 엔드포인트 선택 시 안정적으로 동작")
	void concurrentEndpointSelection_worksStably() throws InterruptedException {
		// Given
		int threadCount = 100;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		AtomicInteger successCount = new AtomicInteger(0);

		// When: 100개 스레드에서 동시에 엔드포인트 선택
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					TtsEndpoint selected = loadBalancer.selectEndpoint();
					if (selected != null && selected.isAvailable()) {
						successCount.incrementAndGet();
					}
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(5, TimeUnit.SECONDS);
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.SECONDS);

		// Then: 모든 선택이 성공
		assertThat(successCount.get()).isEqualTo(threadCount);
	}

	@Test
	@DisplayName("[동시성] activeRequests 카운터가 동시 증감 시에도 정확성 유지")
	void concurrentActiveRequestsChanges_maintainsAccuracy() throws InterruptedException {
		// Given
		TtsEndpoint endpoint = endpoints.get(0);
		int operationCount = 1000;
		ExecutorService executor = Executors.newFixedThreadPool(50);
		CountDownLatch latch = new CountDownLatch(operationCount * 2);

		// When: 1000번 증가, 1000번 감소를 동시에 수행
		for (int i = 0; i < operationCount; i++) {
			executor.submit(() -> {
				try {
					endpoint.incrementActiveRequests();
				} finally {
					latch.countDown();
				}
			});
			executor.submit(() -> {
				try {
					endpoint.decrementActiveRequests();
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(10, TimeUnit.SECONDS);
		executor.shutdown();
		executor.awaitTermination(10, TimeUnit.SECONDS);

		// Then: activeRequests가 0이어야 함 (모든 증가/감소가 정확히 처리됨)
		assertThat(endpoint.getActiveRequests()).isEqualTo(0);
	}

	// ==================== 순환 재시도 로직 테스트 ====================

	@Test
	@DisplayName("[재시도] 첫 번째 엔드포인트 실패 시 다음 엔드포인트로 순환 재시도")
	void retryWithNextEndpoint_whenFirstFails() {
		// Given: endpoint-1만 TEMPORARY_FAILURE
		endpoints.get(0).setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);

		// When: 5번 선택
		List<String> selectedIds = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			selectedIds.add(loadBalancer.selectEndpoint().getId());
		}

		// Then: endpoint-2, endpoint-3만 선택됨 (endpoint-1 제외)
		assertThat(selectedIds).doesNotContain("endpoint-1");
		assertThat(selectedIds).allMatch(id -> id.equals("endpoint-2") || id.equals("endpoint-3"));
	}

	@Test
	@DisplayName("[재시도] 모든 HEALTHY 엔드포인트 실패 시 TEMPORARY_FAILURE 엔드포인트 선택")
	void selectTemporaryFailureEndpoint_whenAllHealthyFail() {
		// Given: endpoint-1,2는 PERMANENT_FAILURE, endpoint-3은 TEMPORARY_FAILURE
		endpoints.get(0).setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);
		endpoints.get(1).setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);
		endpoints.get(2).setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);

		// When
		TtsEndpoint selected = loadBalancer.selectEndpoint();

		// Then: TEMPORARY_FAILURE 엔드포인트 선택 (복구 가능성 있음)
		assertThat(selected.getId()).isEqualTo("endpoint-3");
		assertThat(selected.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
	}

	@Test
	@DisplayName("[재시도] 여러 TEMPORARY_FAILURE 엔드포인트 중 첫 번째 선택")
	void selectFirstTemporaryFailure_whenMultipleAvailable() {
		// Given: 모든 엔드포인트가 TEMPORARY_FAILURE
		endpoints.forEach(e -> e.setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE));

		// When
		TtsEndpoint selected = loadBalancer.selectEndpoint();

		// Then: 첫 번째 TEMPORARY_FAILURE 엔드포인트 선택
		assertThat(selected).isEqualTo(endpoints.get(0));
	}

	// ==================== 장애 복구 시나리오 테스트 ====================

	@Test
	@DisplayName("[복구] TEMPORARY_FAILURE 엔드포인트가 성공 후 HEALTHY로 복구되어 다시 선택됨")
	void temporaryFailureEndpoint_recoversAndGetsSelected() {
		// Given: endpoint-1이 TEMPORARY_FAILURE 상태
		TtsEndpoint endpoint1 = endpoints.get(0);
		endpoint1.setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);

		// When: 성공 보고
		loadBalancer.reportSuccess(endpoint1);

		// Then: HEALTHY로 복구되고 다시 선택 가능
		assertThat(endpoint1.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY);
		assertThat(endpoint1.isAvailable()).isTrue();

		// 여러 번 선택 시 endpoint-1도 포함됨
		List<String> selectedIds = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			selectedIds.add(loadBalancer.selectEndpoint().getId());
		}
		assertThat(selectedIds).contains("endpoint-1");
	}

	@Test
	@DisplayName("[복구] PERMANENT_FAILURE 엔드포인트는 circuit breaker가 OPEN 상태라 선택 불가")
	void permanentFailureEndpoint_cannotBeSelected() {
		// Given: endpoint-1에 PERMANENT 에러 발생
		TtsEndpoint endpoint1 = endpoints.get(0);
		Exception error401 = WebClientResponseException
			.create(401, "Unauthorized", null, null, null);
		loadBalancer.reportFailure(endpoint1, error401);

		// Then: PERMANENT_FAILURE 상태 + circuit breaker OPEN
		assertThat(endpoint1.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);
		assertThat(endpoint1.isAvailable()).isFalse();
		assertThat(endpoint1.getCircuitBreaker().allowRequest()).isFalse();

		// When: 엔드포인트 선택 시 endpoint-1은 선택되지 않음
		List<String> selectedIds = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			selectedIds.add(loadBalancer.selectEndpoint().getId());
		}
		assertThat(selectedIds).doesNotContain("endpoint-1");
	}

	@Test
	@DisplayName("[복구] 여러 엔드포인트가 순차적으로 장애/복구 시 올바르게 동작")
	void multipleEndpoints_failAndRecover_sequentially() {
		// Given: 초기 상태 (모두 HEALTHY)

		// When & Then: endpoint-1 장애 발생
		Exception error429 = WebClientResponseException
			.create(429, "Too Many Requests", null, null, null);
		loadBalancer.reportFailure(endpoints.get(0), error429);
		assertThat(endpoints.get(0).getHealth())
			.isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
		assertThat(endpoints.get(0).getCircuitBreaker().getState())
			.isEqualTo(CircuitBreakerState.OPEN);

		// circuit breaker OPEN 상태라 선택 불가
		List<String> afterFirstFailure = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			afterFirstFailure.add(loadBalancer.selectEndpoint().getId());
		}
		assertThat(afterFirstFailure).doesNotContain("endpoint-1");

		// endpoint-1 health 복구
		loadBalancer.reportSuccess(endpoints.get(0));

		// Health는 HEALTHY로 복구되지만, circuit breaker는 여전히 OPEN
		// (실제로는 백오프 시간이 지나야 HALF_OPEN으로 전이)
		assertThat(endpoints.get(0).getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY);
		assertThat(endpoints.get(0).getCircuitBreaker().getState())
			.isEqualTo(CircuitBreakerState.OPEN);
	}

	@Test
	@DisplayName("[복구] 모든 엔드포인트 장애 후 하나씩 복구되는 시나리오")
	void allEndpointsFail_thenRecoverOneByOne() {
		// Given: 모든 엔드포인트가 TEMPORARY_FAILURE
		Exception error503 = WebClientResponseException
			.create(503, "Service Unavailable", null, null, null);
		endpoints.forEach(e -> loadBalancer.reportFailure(e, error503));

		// Then: 모두 TEMPORARY_FAILURE, circuit breaker OPEN
		endpoints.forEach(e -> {
			assertThat(e.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
			assertThat(e.getCircuitBreaker().getState()).isEqualTo(CircuitBreakerState.OPEN);
		});

		// When: 모든 엔드포인트 장애 상태에서 selectEndpoint() 호출 시
		// fallback으로 TEMPORARY_FAILURE 엔드포인트 중 하나를 반환
		TtsEndpoint selected = loadBalancer.selectEndpoint();
		assertThat(selected.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);

		// When: endpoint-1을 reportSuccess()로 복구
		loadBalancer.reportSuccess(endpoints.get(0));

		// Then: endpoint-1만 HEALTHY, circuit breaker는 여전히 OPEN
		assertThat(endpoints.get(0).getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY);
		assertThat(endpoints.get(0).getCircuitBreaker().getState())
			.isEqualTo(CircuitBreakerState.OPEN);

		// Circuit breaker가 OPEN이라 canAcceptRequest() = false
		assertThat(endpoints.get(0).canAcceptRequest()).isFalse();

		// 여전히 fallback으로 TEMPORARY_FAILURE 엔드포인트 반환
		TtsEndpoint afterRecovery = loadBalancer.selectEndpoint();
		assertThat(afterRecovery.getHealth())
			.isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
	}
}
