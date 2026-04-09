package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.CircuitBreakerState
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException

class TtsLoadBalancerTest {
    private lateinit var endpoints: MutableList<TtsEndpoint>
    private lateinit var loadBalancer: TtsLoadBalancer

    @BeforeEach
    fun setUp() {
        endpoints =
            mutableListOf(
                TtsEndpoint("endpoint-1", "key-1", "http://localhost:8080"),
                TtsEndpoint("endpoint-2", "key-2", "http://localhost:8080"),
                TtsEndpoint("endpoint-3", "key-3", "http://localhost:8080"),
            )
        loadBalancer = TtsLoadBalancer(endpoints)
        endpoints.forEach { it.updateCredits(100.0) }
    }

    @Test
    @DisplayName("사용할 수 없는 endpoint는 선택 대상에서 제외한다")
    fun selectEndpoint_skipsUnavailableEndpoints() {
        endpoints[0].health = TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE
        endpoints[1].health = TtsEndpoint.EndpointHealth.PERMANENT_FAILURE

        val selected = loadBalancer.selectEndpoint()

        assertThat(selected.id).isEqualTo("endpoint-3")
        assertThat(selected.canAcceptRequest()).isTrue()
    }

    @Test
    @DisplayName("충전 크레딧이 가장 낮은 endpoint를 우선 선택한다")
    fun selectEndpoint_prefersLowestCreditEndpoint() {
        endpoints[0].updateCredits(200.0)
        endpoints[1].updateCredits(50.0)
        endpoints[2].updateCredits(200.0)

        val selected = loadBalancer.selectEndpoint()

        assertThat(selected.id).isEqualTo("endpoint-2")
        assertThat(selected.credits).isEqualTo(50.0)
    }

    @Test
    @DisplayName("일시 실패를 보고하면 회로를 열고 상태를 temporary failure로 표시한다")
    fun reportFailure_marksTemporaryFailure() {
        val endpoint = endpoints[0]

        loadBalancer.reportFailure(endpoint, responseException(429, "Too Many Requests"))

        assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
        assertThat(endpoint.circuitOpenedAt).isNotNull()
        assertThat(endpoint.circuitBreaker.state).isEqualTo(CircuitBreakerState.OPEN)
    }

    @Test
    @DisplayName("영구 실패를 보고하면 실패 이벤트를 발행한다")
    fun reportFailure_publishesPermanentFailureEvent() {
        val endpoint = endpoints[0]
        var event: TtsEndpointFailureEvent? = null

        loadBalancer.setFailureEventPublisher { published -> event = published }

        loadBalancer.reportFailure(endpoint, responseException(402, "Payment Required"))

        assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE)
        assertThat(endpoint.circuitBreaker.state).isEqualTo(CircuitBreakerState.OPEN)
        assertThat(event).isNotNull
        assertThat(event!!.endpointId).isEqualTo("endpoint-1")
        assertThat(event!!.errorType).isEqualTo("PERMANENT_FAILURE")
    }

    @Test
    @DisplayName("클라이언트 오류는 endpoint를 healthy 상태로 유지한다")
    fun reportFailure_keepsEndpointHealthyForClientError() {
        val endpoint = endpoints[0]

        loadBalancer.reportFailure(endpoint, responseException(400, "Bad Request"))

        assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY)
        assertThat(endpoint.canAcceptRequest()).isTrue()
    }

    @Test
    @DisplayName("성공을 보고하면 temporary failure endpoint를 healthy로 복구한다")
    fun reportSuccess_recoversTemporaryFailureEndpoint() {
        val endpoint = endpoints[0]
        endpoint.health = TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE

        loadBalancer.reportSuccess(endpoint)

        assertThat(endpoint.health).isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY)
    }

    @Test
    @DisplayName("필요하면 temporary failure endpoint를 대체 후보로 선택한다")
    fun selectEndpoint_usesTemporaryFailureFallback() {
        endpoints[0].health = TtsEndpoint.EndpointHealth.PERMANENT_FAILURE
        endpoints[1].health = TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE
        endpoints[2].health = TtsEndpoint.EndpointHealth.PERMANENT_FAILURE

        val selected = loadBalancer.selectEndpoint()

        assertThat(selected.id).isEqualTo("endpoint-2")
        assertThat(selected.health).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
    }

    @Test
    @DisplayName("사용 가능한 endpoint가 없으면 예외가 발생한다")
    fun selectEndpoint_throwsWhenNoEndpointIsAvailable() {
        endpoints.forEach { it.health = TtsEndpoint.EndpointHealth.PERMANENT_FAILURE }

        assertThatThrownBy { loadBalancer.selectEndpoint() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No available TTS endpoint")
    }

    private fun responseException(
        statusCode: Int,
        statusText: String,
    ): WebClientResponseException =
        WebClientResponseException.create(
            statusCode,
            statusText,
            HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )
}
