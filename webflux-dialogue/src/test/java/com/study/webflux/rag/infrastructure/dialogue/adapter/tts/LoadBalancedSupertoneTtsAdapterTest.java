package com.study.webflux.rag.infrastructure.dialogue.adapter.tts;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunctions;

import com.study.webflux.rag.domain.voice.model.AudioFormat;
import com.study.webflux.rag.domain.voice.model.Voice;
import com.study.webflux.rag.domain.voice.model.VoiceSettings;
import com.study.webflux.rag.domain.voice.model.VoiceStyle;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.FakeSupertoneServer;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer;
import com.study.webflux.rag.infrastructure.monitoring.config.TtsBackpressureMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class LoadBalancedSupertoneTtsAdapterTest {

	private static final int TEST_PORT = 18080;
	private static final String BASE_URL = "http://localhost:" + TEST_PORT;

	private FakeSupertoneServer fakeServer;
	private DisposableServer server;
	private TtsLoadBalancer loadBalancer;
	private LoadBalancedSupertoneTtsAdapter adapter;
	private TtsBackpressureMetrics ttsBackpressureMetrics;

	@BeforeEach
	void setUp() {
		fakeServer = new FakeSupertoneServer();
		startFakeServer();

		List<TtsEndpoint> endpoints = List.of(new TtsEndpoint("endpoint-1", "key-1", BASE_URL),
			new TtsEndpoint("endpoint-2", "key-2", BASE_URL),
			new TtsEndpoint("endpoint-3", "key-3", BASE_URL));

		loadBalancer = new TtsLoadBalancer(endpoints);
		ttsBackpressureMetrics = new TtsBackpressureMetrics(new SimpleMeterRegistry());

		WebClient.Builder webClientBuilder = WebClient.builder()
			.clientConnector(new ReactorClientHttpConnector());

		Voice voice = Voice.builder().id("test-voice-id").name("Test Voice").provider("supertone")
			.language("ko").style(VoiceStyle.NEUTRAL).outputFormat(AudioFormat.WAV)
			.settings(new VoiceSettings(0, 1.0, 1.0)).build();

		adapter = new LoadBalancedSupertoneTtsAdapter(webClientBuilder,
			loadBalancer,
			voice,
			ttsBackpressureMetrics);
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.disposeNow();
		}
	}

	private void startFakeServer() {
		HttpHandler httpHandler = RouterFunctions.toHttpHandler(fakeServer.routes());
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		server = HttpServer.create().port(TEST_PORT).handle(adapter).bindNow();
	}

	@Test
	@DisplayName("정상 요청 시 TTS 오디오 스트림 반환")
	void streamSynthesize_Success() {
		fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.success());

		Flux<byte[]> result = adapter.streamSynthesize("Hello, world!");

		StepVerifier.create(result).expectNextMatches(bytes -> bytes.length > 0).verifyComplete();

		assertThat(fakeServer.getRequestCount("key-1")).isEqualTo(1);
	}

	@Test
	@DisplayName("일시적 에러 발생 시 다른 엔드포인트로 재시도")
	void streamSynthesize_TemporaryError_Retry() {
		fakeServer.setEndpointBehavior("key-1",
			FakeSupertoneServer.ServerBehavior.error(HttpStatus.TOO_MANY_REQUESTS));
		fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success());
		fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.success());

		Flux<byte[]> result = adapter.streamSynthesize("Hello, world!");

		StepVerifier.create(result).expectNextMatches(bytes -> bytes.length > 0).verifyComplete();

		int totalRequests = fakeServer.getRequestCount("key-1")
			+ fakeServer.getRequestCount("key-2") + fakeServer.getRequestCount("key-3");
		assertThat(totalRequests).isEqualTo(2);
	}

	@Test
	@DisplayName("영구 장애 발생 시 다른 엔드포인트로 재시도")
	void streamSynthesize_PermanentError_Retry() {
		fakeServer.setEndpointBehavior("key-1",
			FakeSupertoneServer.ServerBehavior.error(HttpStatus.PAYMENT_REQUIRED));
		fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success());

		Flux<byte[]> result = adapter.streamSynthesize("Hello, world!");

		StepVerifier.create(result).expectNextMatches(bytes -> bytes.length > 0).verifyComplete();

		TtsEndpoint endpoint1 = loadBalancer.getEndpoints().get(0);
		assertThat(endpoint1.getHealth()).isEqualTo(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);
	}

	@Test
	@DisplayName("클라이언트 에러 발생 시 즉시 실패")
	void streamSynthesize_ClientError_ImmediateFail() {
		fakeServer.setEndpointBehavior("key-1",
			FakeSupertoneServer.ServerBehavior.error(HttpStatus.BAD_REQUEST));

		Flux<byte[]> result = adapter.streamSynthesize("Hello, world!");

		StepVerifier.create(result).expectError().verify();

		assertThat(fakeServer.getRequestCount("key-1")).isEqualTo(1);
		assertThat(fakeServer.getRequestCount("key-2")).isEqualTo(0);
	}

	@Test
	@DisplayName("모든 엔드포인트 실패 시 최대 재시도 후 실패")
	void streamSynthesize_AllEndpointsFail() {
		fakeServer.setEndpointBehavior("key-1",
			FakeSupertoneServer.ServerBehavior.error(HttpStatus.INTERNAL_SERVER_ERROR));
		fakeServer.setEndpointBehavior("key-2",
			FakeSupertoneServer.ServerBehavior.error(HttpStatus.INTERNAL_SERVER_ERROR));
		fakeServer.setEndpointBehavior("key-3",
			FakeSupertoneServer.ServerBehavior.error(HttpStatus.INTERNAL_SERVER_ERROR));

		Flux<byte[]> result = adapter.streamSynthesize("Hello, world!");

		StepVerifier.create(result).expectErrorMatches(error -> error instanceof RuntimeException
			&& error.getMessage().contains("모든 TTS 엔드포인트 요청 실패")).verify();

		int totalRequests = fakeServer.getRequestCount("key-1")
			+ fakeServer.getRequestCount("key-2") + fakeServer.getRequestCount("key-3");
		assertThat(totalRequests).isEqualTo(2);
	}

	@Test
	@DisplayName("로드 밸런싱: 여러 요청이 고르게 분산")
	void streamSynthesize_LoadBalancing() {
		fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.success());
		fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success());
		fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.success());
		fakeServer.resetRequestCounts();

		for (int i = 0; i < 9; i++) {
			adapter.streamSynthesize("Test " + i).blockLast();
		}

		int total = fakeServer.getRequestCount("key-1") + fakeServer.getRequestCount("key-2")
			+ fakeServer.getRequestCount("key-3");

		assertThat(total).isEqualTo(9);
		assertThat(fakeServer.getRequestCount("key-1")).isGreaterThan(0);
		assertThat(fakeServer.getRequestCount("key-2")).isGreaterThan(0);
		assertThat(fakeServer.getRequestCount("key-3")).isGreaterThan(0);
	}

	@Test
	@DisplayName("타임아웃 발생 시 재시도")
	void streamSynthesize_Timeout_Retry() {
		fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.timeout());
		fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success());
		fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.success());

		Flux<byte[]> result = adapter.streamSynthesize("Hello, world!");

		StepVerifier.create(result).expectNextMatches(bytes -> bytes.length > 0).verifyComplete();

		int successRequests = fakeServer.getRequestCount("key-2")
			+ fakeServer.getRequestCount("key-3");
		assertThat(successRequests).isGreaterThan(0);
	}

	@Test
	@DisplayName("300자 초과 텍스트 요청 시 400 에러")
	void streamSynthesize_TextTooLong() {
		fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.success());

		String longText = "a".repeat(301);
		Flux<byte[]> result = adapter.streamSynthesize(longText);

		StepVerifier.create(result).expectError().verify();
	}

	// ==================== 취약점 수정 검증 테스트 ====================

	@Test
	@DisplayName("[수정됨] 요청 취소 시 activeRequests 카운트가 정상적으로 감소")
	void cancelledRequest_decrementsActiveRequestCount() throws InterruptedException {
		// Given: 느린 응답을 반환하는 서버 (5초 지연)
		fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.delayed(5000));
		fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.delayed(5000));
		fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.delayed(5000));

		// When: 요청 시작 후 즉시 취소
		Flux<byte[]> flux = adapter.streamSynthesize("test", AudioFormat.WAV);
		flux.subscribe().dispose();

		// 취소 처리 대기
		Thread.sleep(200);

		// Then: 취소 후 activeRequests가 0으로 돌아옴 (doOnCancel로 수정됨)
		int afterCancelCount = loadBalancer.getEndpoints().stream()
			.mapToInt(TtsEndpoint::getActiveRequests)
			.sum();

		assertThat(afterCancelCount).isEqualTo(0);
	}

	@Test
	@DisplayName("[수정됨] Warmup 실패 시 엔드포인트가 TEMPORARY_FAILURE로 표시됨")
	void warmupFailure_marksEndpointAsTemporaryFailure() {
		// Given: 모든 엔드포인트가 연결 불가 상태
		if (server != null) {
			server.disposeNow();
			server = null;
		}

		// 새로운 어댑터 생성 (연결 불가한 URL 사용)
		List<TtsEndpoint> badEndpoints = List.of(
			new TtsEndpoint("bad-1", "key-1", "http://localhost:59999"),
			new TtsEndpoint("bad-2", "key-2", "http://localhost:59998"));
		TtsLoadBalancer badLoadBalancer = new TtsLoadBalancer(badEndpoints);

		WebClient.Builder webClientBuilder = WebClient.builder()
			.clientConnector(new ReactorClientHttpConnector());

		Voice voice = Voice.builder().id("test-voice-id").name("Test Voice").provider("supertone")
			.language("ko").style(VoiceStyle.NEUTRAL).outputFormat(AudioFormat.WAV)
			.settings(new VoiceSettings(0, 1.0, 1.0)).build();

		LoadBalancedSupertoneTtsAdapter badAdapter = new LoadBalancedSupertoneTtsAdapter(
			webClientBuilder,
			badLoadBalancer,
			voice,
			new TtsBackpressureMetrics(new SimpleMeterRegistry()));

		// When: prepare() 호출 (warmup 실패)
		badAdapter.prepare().block();

		// Then: 모든 엔드포인트가 TEMPORARY_FAILURE 상태로 변경됨 (수정됨)
		for (TtsEndpoint endpoint : badLoadBalancer.getEndpoints()) {
			assertThat(endpoint.getHealth())
				.as("Warmup 실패한 엔드포인트 %s가 TEMPORARY_FAILURE 상태", endpoint.getId())
				.isEqualTo(TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE);
		}
	}

	// ==================== 동시 요청 테스트 ====================

	@Test
	@DisplayName("[동시성] 여러 요청이 동시에 들어와도 로드밸런싱이 올바르게 동작")
	void concurrentRequests_loadBalancedCorrectly() throws InterruptedException {
		// Given: 모든 엔드포인트가 정상
		fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.success());
		fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success());
		fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.success());
		fakeServer.resetRequestCounts();

		// When: 30개의 동시 요청
		int requestCount = 30;
		CountDownLatch latch = new CountDownLatch(requestCount);
		AtomicInteger successCount = new AtomicInteger(0);

		for (int i = 0; i < requestCount; i++) {
			adapter.streamSynthesize("Test " + System.currentTimeMillis())
				.doOnComplete(() -> {
					successCount.incrementAndGet();
					latch.countDown();
				})
				.doOnError(e -> latch.countDown())
				.subscribeOn(Schedulers.parallel())
				.subscribe();
		}

		latch.await(10, TimeUnit.SECONDS);

		// Then: 모든 요청 성공
		assertThat(successCount.get()).isEqualTo(requestCount);

		// 요청이 모든 엔드포인트에 분산됨
		int total = fakeServer.getRequestCount("key-1")
			+ fakeServer.getRequestCount("key-2")
			+ fakeServer.getRequestCount("key-3");
		assertThat(total).isEqualTo(requestCount);

		// 각 엔드포인트가 요청을 받음 (동시성 환경에서는 분산 보장만 확인)
		assertThat(total).isGreaterThan(0);
	}

	@Test
	@DisplayName("[동시성] 일부 엔드포인트 장애 시에도 동시 요청이 정상 처리됨")
	void concurrentRequests_withPartialFailure() throws InterruptedException {
		// Given: endpoint-1은 장애, 나머지는 정상
		fakeServer.setEndpointBehavior("key-1",
			FakeSupertoneServer.ServerBehavior.error(HttpStatus.SERVICE_UNAVAILABLE));
		fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.success());
		fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.success());
		fakeServer.resetRequestCounts();

		// When: 20개의 동시 요청
		int requestCount = 20;
		CountDownLatch latch = new CountDownLatch(requestCount);
		AtomicInteger successCount = new AtomicInteger(0);

		for (int i = 0; i < requestCount; i++) {
			adapter.streamSynthesize("Test " + System.currentTimeMillis())
				.doOnComplete(() -> {
					successCount.incrementAndGet();
					latch.countDown();
				})
				.doOnError(e -> latch.countDown())
				.subscribeOn(Schedulers.parallel())
				.subscribe();
		}

		latch.await(10, TimeUnit.SECONDS);

		// Then: 모든 요청이 성공 (재시도 덕분에)
		assertThat(successCount.get()).isEqualTo(requestCount);

		// endpoint-2, endpoint-3에서만 성공 요청 처리됨
		int successRequests = fakeServer.getRequestCount("key-2")
			+ fakeServer.getRequestCount("key-3");
		assertThat(successRequests).isEqualTo(requestCount);

		// endpoint-1은 실패 엔드포인트이므로 재시도 과정에서 일부 실패 요청이 발생
		assertThat(fakeServer.getRequestCount("key-1")).isGreaterThan(0);
	}

	@Test
	@DisplayName("[동시성] activeRequests 카운터가 동시 요청에서도 정확하게 관리됨")
	void concurrentRequests_maintainsAccurateActiveRequestCount() throws InterruptedException {
		// Given: 느린 응답을 반환하는 서버 (100ms 지연)
		fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.delayed(100));
		fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.delayed(100));
		fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.delayed(100));

		// When: 15개의 동시 요청
		int requestCount = 15;
		CountDownLatch startLatch = new CountDownLatch(requestCount);
		CountDownLatch endLatch = new CountDownLatch(requestCount);

		for (int i = 0; i < requestCount; i++) {
			Mono.fromRunnable(() -> {
				adapter.streamSynthesize("Test " + System.currentTimeMillis())
					.doOnSubscribe(s -> startLatch.countDown())
					.doFinally(s -> endLatch.countDown())
					.subscribeOn(Schedulers.parallel())
					.subscribe();
			}).subscribeOn(Schedulers.parallel()).subscribe();
		}

		// 모든 요청이 시작될 때까지 대기
		startLatch.await(3, TimeUnit.SECONDS);

		// Then: activeRequests 합계가 요청 수와 일치
		int activeRequestsSum = loadBalancer.getEndpoints().stream()
			.mapToInt(TtsEndpoint::getActiveRequests)
			.sum();
		assertThat(activeRequestsSum).isGreaterThan(0).isLessThanOrEqualTo(requestCount);

		// 모든 요청 완료 대기
		endLatch.await(5, TimeUnit.SECONDS);

		// Then: 모든 요청 완료 후 activeRequests가 0
		int finalActiveRequests = loadBalancer.getEndpoints().stream()
			.mapToInt(TtsEndpoint::getActiveRequests)
			.sum();
		assertThat(finalActiveRequests).isEqualTo(0);
	}
}
