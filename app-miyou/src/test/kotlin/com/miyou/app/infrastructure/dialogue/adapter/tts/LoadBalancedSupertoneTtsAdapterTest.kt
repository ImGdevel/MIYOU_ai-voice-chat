package com.miyou.app.infrastructure.dialogue.adapter.tts

import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.domain.voice.model.Voice
import com.miyou.app.domain.voice.model.VoiceSettings
import com.miyou.app.domain.voice.model.VoiceStyle
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.FakeSupertoneServer
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.RouterFunctions
import reactor.core.scheduler.Schedulers
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import reactor.test.StepVerifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LoadBalancedSupertoneTtsAdapterTest {
    private lateinit var fakeServer: FakeSupertoneServer
    private var server: DisposableServer? = null
    private lateinit var loadBalancer: TtsLoadBalancer
    private lateinit var adapter: LoadBalancedSupertoneTtsAdapter

    @BeforeEach
    fun setUp() {
        fakeServer = FakeSupertoneServer()
        startFakeServer()

        val endpoints =
            listOf(
                TtsEndpoint("endpoint-1", "key-1", BASE_URL),
                TtsEndpoint("endpoint-2", "key-2", BASE_URL),
                TtsEndpoint("endpoint-3", "key-3", BASE_URL),
            )
        endpoints.forEach { it.updateCredits(100.0) }

        loadBalancer = TtsLoadBalancer(endpoints)
        adapter =
            LoadBalancedSupertoneTtsAdapter(
                WebClient.builder().clientConnector(ReactorClientHttpConnector()),
                loadBalancer,
                testVoice(),
            )
    }

    @AfterEach
    fun tearDown() {
        server?.disposeNow()
        server = null
    }

    @Test
    @DisplayName("정상 요청이면 오디오 스트림을 반환한다")
    fun streamSynthesizeSuccess() {
        fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.success())

        StepVerifier
            .create(adapter.streamSynthesize("Hello, world!"))
            .expectNextMatches { it.isNotEmpty() }
            .verifyComplete()

        assertThat(fakeServer.getRequestCount("key-1")).isEqualTo(1)
    }

    @Test
    @DisplayName("temporary error가 나면 다른 endpoint로 재시도한다")
    fun streamSynthesizeTemporaryErrorRetry() {
        fakeServer.setEndpointBehavior(
            "key-1",
            FakeSupertoneServer.ServerBehavior.error(HttpStatus.TOO_MANY_REQUESTS),
        )
        fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success())
        fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.success())

        StepVerifier
            .create(adapter.streamSynthesize("Hello, world!"))
            .expectNextMatches { it.isNotEmpty() }
            .verifyComplete()

        assertThat(requestCount("key-1", "key-2", "key-3")).isEqualTo(2)
    }

    @Test
    @DisplayName("permanent error가 나면 다른 endpoint로 재시도한다")
    fun streamSynthesizePermanentErrorRetry() {
        fakeServer.setEndpointBehavior(
            "key-1",
            FakeSupertoneServer.ServerBehavior.error(HttpStatus.PAYMENT_REQUIRED),
        )
        fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success())

        StepVerifier
            .create(adapter.streamSynthesize("Hello, world!"))
            .expectNextMatches { it.isNotEmpty() }
            .verifyComplete()

        assertThat(loadBalancer.endpoints[0].health)
            .isEqualTo(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE)
    }

    @Test
    @DisplayName("client error는 즉시 실패한다")
    fun streamSynthesizeClientErrorImmediateFail() {
        fakeServer.setEndpointBehavior(
            "key-1",
            FakeSupertoneServer.ServerBehavior.error(HttpStatus.BAD_REQUEST),
        )

        StepVerifier
            .create(adapter.streamSynthesize("Hello, world!"))
            .expectError()
            .verify()

        assertThat(fakeServer.getRequestCount("key-1")).isEqualTo(1)
        assertThat(fakeServer.getRequestCount("key-2")).isEqualTo(0)
    }

    @Test
    @DisplayName("모든 endpoint가 실패하면 최대 재시도 후 실패한다")
    fun streamSynthesizeAllEndpointsFail() {
        fakeServer.setEndpointBehavior(
            "key-1",
            FakeSupertoneServer.ServerBehavior.error(HttpStatus.INTERNAL_SERVER_ERROR),
        )
        fakeServer.setEndpointBehavior(
            "key-2",
            FakeSupertoneServer.ServerBehavior.error(HttpStatus.INTERNAL_SERVER_ERROR),
        )
        fakeServer.setEndpointBehavior(
            "key-3",
            FakeSupertoneServer.ServerBehavior.error(HttpStatus.INTERNAL_SERVER_ERROR),
        )

        StepVerifier
            .create(adapter.streamSynthesize("Hello, world!"))
            .expectErrorMatches {
                it is RuntimeException && it.message!!.contains("모든 TTS 엔드포인트 요청 실패")
            }.verify()

        assertThat(requestCount("key-1", "key-2", "key-3")).isEqualTo(2)
    }

    @Test
    @DisplayName("여러 요청을 endpoint에 분산한다")
    fun streamSynthesizeLoadBalancing() {
        fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.success())
        fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success())
        fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.success())
        fakeServer.resetRequestCounts()

        repeat(9) { index ->
            adapter.streamSynthesize("Test $index").blockLast()
        }

        assertThat(requestCount("key-1", "key-2", "key-3")).isEqualTo(9)
        assertThat(fakeServer.getRequestCount("key-1")).isGreaterThan(0)
        assertThat(fakeServer.getRequestCount("key-2")).isGreaterThan(0)
        assertThat(fakeServer.getRequestCount("key-3")).isGreaterThan(0)
    }

    @Test
    @DisplayName("timeout이 발생하면 다른 endpoint로 재시도한다")
    fun streamSynthesizeTimeoutRetry() {
        fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.timeout())
        fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success())
        fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.success())

        StepVerifier
            .create(adapter.streamSynthesize("Hello, world!"))
            .expectNextMatches { it.isNotEmpty() }
            .verifyComplete()

        assertThat(requestCount("key-2", "key-3")).isGreaterThan(0)
    }

    @Test
    @DisplayName("300자를 넘는 텍스트는 400 에러로 실패한다")
    fun streamSynthesizeTextTooLong() {
        fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.success())

        StepVerifier
            .create(adapter.streamSynthesize("a".repeat(301)))
            .expectError()
            .verify()
    }

    @Test
    @DisplayName("요청 취소 후 active request count를 감소시킨다")
    fun cancelledRequestDecrementsActiveRequestCount() {
        fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.delayed(5000))
        fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.delayed(5000))
        fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.delayed(5000))

        adapter.streamSynthesize("test", AudioFormat.WAV).subscribe().dispose()
        Thread.sleep(200)

        val activeRequestCount = loadBalancer.endpoints.sumOf { it.activeRequests }
        assertThat(activeRequestCount).isEqualTo(0)
    }

    @Test
    @DisplayName("warmup 실패 시 endpoint를 temporary failure로 표시한다")
    fun warmupFailureMarksEndpointAsTemporaryFailure() {
        server?.disposeNow()
        server = null

        val badLoadBalancer =
            TtsLoadBalancer(
                listOf(
                    TtsEndpoint("bad-1", "key-1", "http://localhost:59999"),
                    TtsEndpoint("bad-2", "key-2", "http://localhost:59998"),
                ),
            )
        val badAdapter =
            LoadBalancedSupertoneTtsAdapter(
                WebClient.builder().clientConnector(ReactorClientHttpConnector()),
                badLoadBalancer,
                testVoice(),
            )

        badAdapter.prepare().block()

        badLoadBalancer.endpoints.forEach {
            assertThat(it.health).isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE)
        }
    }

    @Test
    @DisplayName("동시 요청도 load balancing으로 처리한다")
    fun concurrentRequestsLoadBalancedCorrectly() {
        fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.success())
        fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success())
        fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.success())
        fakeServer.resetRequestCounts()

        val requestCount = 30
        val latch = CountDownLatch(requestCount)
        val successCount = AtomicInteger(0)

        repeat(requestCount) { index ->
            adapter
                .streamSynthesize("Test $index")
                .doOnComplete {
                    successCount.incrementAndGet()
                    latch.countDown()
                }.doOnError { latch.countDown() }
                .subscribeOn(Schedulers.parallel())
                .subscribe()
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
        assertThat(successCount.get()).isEqualTo(requestCount)
        assertThat(requestCount("key-1", "key-2", "key-3")).isEqualTo(requestCount)
    }

    @Test
    @DisplayName("일부 endpoint가 실패해도 동시 요청을 처리한다")
    fun concurrentRequestsWithPartialFailure() {
        fakeServer.setEndpointBehavior(
            "key-1",
            FakeSupertoneServer.ServerBehavior.error(HttpStatus.SERVICE_UNAVAILABLE),
        )
        fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success())
        fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.success())
        fakeServer.resetRequestCounts()

        val requestCount = 20
        val latch = CountDownLatch(requestCount)
        val successCount = AtomicInteger(0)

        repeat(requestCount) { index ->
            adapter
                .streamSynthesize("Test $index")
                .doOnComplete {
                    successCount.incrementAndGet()
                    latch.countDown()
                }.doOnError { latch.countDown() }
                .subscribeOn(Schedulers.parallel())
                .subscribe()
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
        assertThat(successCount.get()).isEqualTo(requestCount)
        assertThat(requestCount("key-2", "key-3")).isEqualTo(requestCount)
        assertThat(fakeServer.getRequestCount("key-1")).isGreaterThan(0)
    }

    @Test
    @DisplayName("동시 요청 중 active request count를 정확히 관리한다")
    fun concurrentRequestsMaintainsAccurateActiveRequestCount() {
        fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.delayed(100))
        fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.delayed(100))
        fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.delayed(100))

        val requestCount = 15
        val startLatch = CountDownLatch(requestCount)
        val endLatch = CountDownLatch(requestCount)

        repeat(requestCount) { index ->
            Schedulers.parallel().schedule {
                adapter
                    .streamSynthesize("Test $index")
                    .doOnSubscribe { startLatch.countDown() }
                    .doFinally { endLatch.countDown() }
                    .subscribeOn(Schedulers.parallel())
                    .subscribe()
            }
        }

        assertThat(startLatch.await(3, TimeUnit.SECONDS)).isTrue()

        val activeRequests = loadBalancer.endpoints.sumOf { it.activeRequests }
        assertThat(activeRequests).isGreaterThan(0).isLessThanOrEqualTo(requestCount)

        assertThat(endLatch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(loadBalancer.endpoints.sumOf { it.activeRequests }).isEqualTo(0)
    }

    private fun startFakeServer() {
        val httpHandler: HttpHandler = RouterFunctions.toHttpHandler(fakeServer.routes())
        val handlerAdapter = ReactorHttpHandlerAdapter(httpHandler)
        server =
            HttpServer
                .create()
                .port(TEST_PORT)
                .handle(handlerAdapter)
                .bindNow()
    }

    private fun testVoice(): Voice =
        Voice
            .builder()
            .id("test-voice-id")
            .name("Test Voice")
            .provider("supertone")
            .language("ko")
            .style(VoiceStyle.NEUTRAL)
            .outputFormat(AudioFormat.WAV)
            .settings(VoiceSettings(0, 1.0, 1.0))
            .build()

    private fun requestCount(vararg apiKeys: String): Int = apiKeys.sumOf { fakeServer.getRequestCount(it) }

    private companion object {
        const val TEST_PORT = 18080
        const val BASE_URL = "http://localhost:$TEST_PORT"
    }
}
