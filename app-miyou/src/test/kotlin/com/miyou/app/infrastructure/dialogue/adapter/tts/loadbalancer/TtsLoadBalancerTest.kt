package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.CircuitBreakerState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClientResponseException

class TtsLoadBalancerTest {

	private lateinit var endpoints: MutableList<TtsEndpoint>
	private lateinit var loadBalancer: TtsLoadBalancer

	@BeforeEach
	fun setUp() {
		endpoints = mutableListOf(
			TtsEndpoint("endpoint-1", "key-1", "http://localhost:8080"),
			TtsEndpoint("endpoint-2", "key-2", "http://localhost:8080"),
			TtsEndpoint("endpoint-3", "key-3", "http://localhost:8080"),
		)
		loadBalancer = TtsLoadBalancer(endpoints)
		endpoints.forEach { it.updateCredits(100.0) }
	}

	@Test
	@DisplayName("healthy endpoint만 선택한다")
	fun selectOnlyHealthyEndpoints() {
		endpoints[0].setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
		endpoints[1].setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE)

		val selected = loadBalancer.selectEndpoint()

		assertThat(selected.id).isEqualTo("endpoint-3")
		assertThat(selected.isAvailable).isTrue()
	}

	@Test
	@DisplayName("credit이 가장 낮은 endpoint를 우선 선택한다")
	fun selectLowestCreditEndpoint() {
		endpoints[0].updateCredits(200.0)
		endpoints[1].updateCredits(50.0)
		endpoints[2].updateCredits(200.0)

		val selected = loadBalancer.selectEndpoint()

		assertThat(selected.id).isEqualTo("endpoint-2")
		assertThat(selected.credits).isEqualTo(50.0)
	}

	@Test
	@DisplayName("같은 credit이면 round robin으로 분산한다")
	fun roundRobinWhenEqualCredits() {
		val selectedIds = (1..6).map { loadBalancer.selectEndpoint().id }

		assertThat(selectedIds).containsExactly(
			"endpoint-1",
			"endpoint-2",
			"endpoint-3",
			"endpoint-1",
			"endpoint-2",
			"endpoint-3",
		)
	}

	@Test
	@DisplayName("temporary failure를 보고하면 endpoint를 일시 장애로 표시한다")
	fun handleTemporaryFailure() {
		val endpoint = endpoints[0]
		val error = WebClientResponseException.create(429, "Too Many Requests", null, null, null)

		loadBalancer.reportFailure(endpoint, error)

		assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
		assertThat(endpoint.circuitOpenedAt).isNotNull()
	}

	@Test
	@DisplayName("permanent failure를 보고하면 이벤트를 발행한다")
	fun handlePermanentFailure() {
		val endpoint = endpoints[0]
		val error = WebClientResponseException.create(402, "Payment Required", null, null, null)
		val eventCount = AtomicInteger(0)

		loadBalancer.setFailureEventPublisher { event ->
			eventCount.incrementAndGet()
			assertThat(event.endpointId).isEqualTo("endpoint-1")
			assertThat(event.errorType).isEqualTo("PERMANENT_FAILURE")
		}

		loadBalancer.reportFailure(endpoint, error)

		assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE)
		assertThat(eventCount.get()).isEqualTo(1)
	}

	@Test
	@DisplayName("client error는 endpoint 상태를 유지한다")
	fun handleClientErrorKeepsEndpointHealthy() {
		val endpoint = endpoints[0]
		val error = WebClientResponseException.create(400, "Bad Request", null, null, null)

		loadBalancer.reportFailure(endpoint, error)

		assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY)
		assertThat(endpoint.isAvailable).isTrue()
	}

	@Test
	@DisplayName("성공을 보고하면 temporary failure endpoint를 healthy로 복구한다")
	fun recoverToHealthyOnSuccess() {
		val endpoint = endpoints[0]
		endpoint.setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)

		loadBalancer.reportSuccess(endpoint)

		assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY)
	}

	@Test
	@DisplayName("모든 endpoint가 영구 장애면 예외를 던진다")
	fun allPermanentFailureThrowsException() {
		endpoints.forEach { it.setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE) }

		assertThatThrownBy { loadBalancer.selectEndpoint() }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("사용 가능한 TTS 엔드포인트가 없습니다")
	}

	@Test
	@DisplayName("healthy endpoint가 없으면 temporary failure endpoint를 고른다")
	fun noHealthySelectsTemporaryFailureFirst() {
		endpoints[0].setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE)
		endpoints[1].setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
		endpoints[2].setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE)

		val selected = loadBalancer.selectEndpoint()

		assertThat(selected.id).isEqualTo("endpoint-2")
		assertThat(selected.health).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
	}

	@Test
	@DisplayName("client error 이후에도 endpoint는 계속 선택된다")
	fun clientErrorEndpointStillSelected() {
		val endpoint = endpoints[0]
		val error = WebClientResponseException.create(400, "Bad Request", null, null, null)

		loadBalancer.reportFailure(endpoint, error)

		val selectedIds = (1..9).map { loadBalancer.selectEndpoint().id }

		assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY)
		assertThat(selectedIds).contains("endpoint-1")
	}

	@Test
	@DisplayName("동시 health 변경에서도 데이터 일관성을 유지한다")
	fun concurrentHealthStateChangesMaintainsDataIntegrity() {
		val endpoint = endpoints[0]
		val threadCount = 50
		val executor = Executors.newFixedThreadPool(threadCount)
		val latch = CountDownLatch(threadCount)

		repeat(threadCount) { index ->
			executor.submit {
				try {
					when (index % 3) {
						0 -> endpoint.setHealth(TtsEndpoint.EndpointHealth.HEALTHY)
						1 -> endpoint.setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
						else -> endpoint.setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE)
					}
				} finally {
					latch.countDown()
				}
			}
		}

		assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
		executor.shutdown()
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()

		when (endpoint.health) {
			TtsEndpoint.EndpointHealth.HEALTHY -> assertThat(endpoint.circuitOpenedAt).isNull()
			TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE,
			TtsEndpoint.EndpointHealth.PERMANENT_FAILURE -> assertThat(endpoint.circuitOpenedAt).isNotNull()
			TtsEndpoint.EndpointHealth.CLIENT_ERROR -> Unit
		}
	}

	@Test
	@DisplayName("동시에 endpoint를 선택해도 안정적으로 동작한다")
	fun concurrentEndpointSelectionWorksStably() {
		val threadCount = 100
		val executor = Executors.newFixedThreadPool(threadCount)
		val latch = CountDownLatch(threadCount)
		val successCount = AtomicInteger(0)

		repeat(threadCount) {
			executor.submit {
				try {
					val selected = loadBalancer.selectEndpoint()
					if (selected.isAvailable) {
						successCount.incrementAndGet()
					}
				} finally {
					latch.countDown()
				}
			}
		}

		assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
		executor.shutdown()
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
		assertThat(successCount.get()).isEqualTo(threadCount)
	}

	@Test
	@DisplayName("active request count를 동시 증감해도 정확성을 유지한다")
	fun concurrentActiveRequestsChangesMaintainsAccuracy() {
		val endpoint = endpoints[0]
		val operationCount = 1000
		val executor = Executors.newFixedThreadPool(50)
		val latch = CountDownLatch(operationCount * 2)

		repeat(operationCount) {
			executor.submit {
				try {
					endpoint.incrementActiveRequests()
				} finally {
					latch.countDown()
				}
			}
			executor.submit {
				try {
					endpoint.decrementActiveRequests()
				} finally {
					latch.countDown()
				}
			}
		}

		assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
		executor.shutdown()
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
		assertThat(endpoint.activeRequests).isEqualTo(0)
	}

	@Test
	@DisplayName("healthy endpoint가 실패하면 다음 endpoint로 분산한다")
	fun retryWithNextEndpointWhenFirstFails() {
		endpoints[0].setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)

		val selectedIds = (1..5).map { loadBalancer.selectEndpoint().id }

		assertThat(selectedIds).doesNotContain("endpoint-1")
		assertThat(selectedIds).allMatch { it == "endpoint-2" || it == "endpoint-3" }
	}

	@Test
	@DisplayName("healthy가 없으면 temporary failure endpoint를 선택한다")
	fun selectTemporaryFailureEndpointWhenAllHealthyFail() {
		endpoints[0].setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE)
		endpoints[1].setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE)
		endpoints[2].setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)

		val selected = loadBalancer.selectEndpoint()

		assertThat(selected.id).isEqualTo("endpoint-3")
		assertThat(selected.health).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
	}

	@Test
	@DisplayName("temporary failure endpoint가 여러 개면 첫 endpoint를 선택한다")
	fun selectFirstTemporaryFailureWhenMultipleAvailable() {
		endpoints.forEach { it.setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE) }

		val selected = loadBalancer.selectEndpoint()

		assertThat(selected).isEqualTo(endpoints[0])
	}

	@Test
	@DisplayName("temporary failure endpoint는 성공 보고 후 다시 선택된다")
	fun temporaryFailureEndpointRecoversAndGetsSelected() {
		val endpoint = endpoints[0]
		endpoint.setHealth(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)

		loadBalancer.reportSuccess(endpoint)

		val selectedIds = (1..9).map { loadBalancer.selectEndpoint().id }

		assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY)
		assertThat(selectedIds).contains("endpoint-1")
	}

	@Test
	@DisplayName("permanent failure endpoint는 circuit breaker가 열려 다시 선택되지 않는다")
	fun permanentFailureEndpointCannotBeSelected() {
		val endpoint = endpoints[0]
		val error = WebClientResponseException.create(401, "Unauthorized", null, null, null)

		loadBalancer.reportFailure(endpoint, error)

		val selectedIds = (1..10).map { loadBalancer.selectEndpoint().id }

		assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE)
		assertThat(endpoint.isAvailable).isFalse()
		assertThat(endpoint.circuitBreaker.allowRequest()).isFalse()
		assertThat(selectedIds).doesNotContain("endpoint-1")
	}

	@Test
	@DisplayName("여러 endpoint가 순차적으로 장애와 복구를 거친다")
	fun multipleEndpointsFailAndRecoverSequentially() {
		val error = WebClientResponseException.create(429, "Too Many Requests", null, null, null)

		loadBalancer.reportFailure(endpoints[0], error)

		assertThat(endpoints[0].health).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
		assertThat(endpoints[0].circuitBreaker.state).isEqualTo(CircuitBreakerState.OPEN)

		val selectedAfterFailure = (1..6).map { loadBalancer.selectEndpoint().id }
		assertThat(selectedAfterFailure).doesNotContain("endpoint-1")

		loadBalancer.reportSuccess(endpoints[0])

		assertThat(endpoints[0].health).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY)
		assertThat(endpoints[0].circuitBreaker.state).isEqualTo(CircuitBreakerState.OPEN)
	}

	@Test
	@DisplayName("모든 endpoint가 실패해도 temporary failure endpoint를 fallback으로 반환한다")
	fun allEndpointsFailThenRecoverOneByOne() {
		val error = WebClientResponseException.create(503, "Service Unavailable", null, null, null)
		endpoints.forEach { loadBalancer.reportFailure(it, error) }

		endpoints.forEach {
			assertThat(it.health).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
			assertThat(it.circuitBreaker.state).isEqualTo(CircuitBreakerState.OPEN)
		}

		val selected = loadBalancer.selectEndpoint()
		assertThat(selected.health).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)

		loadBalancer.reportSuccess(endpoints[0])

		assertThat(endpoints[0].health).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY)
		assertThat(endpoints[0].circuitBreaker.state).isEqualTo(CircuitBreakerState.OPEN)
		assertThat(endpoints[0].canAcceptRequest()).isFalse()

		val fallback = loadBalancer.selectEndpoint()
		assertThat(fallback.health).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
	}
}
