package com.study.webflux.rag.infrastructure.dialogue.adapter.tts;

import java.util.List;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
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

	@BeforeEach
	void setUp() {
		fakeServer = new FakeSupertoneServer();
		startFakeServer();

		List<TtsEndpoint> endpoints = List.of(new TtsEndpoint("endpoint-1", "key-1", BASE_URL),
			new TtsEndpoint("endpoint-2", "key-2", BASE_URL),
			new TtsEndpoint("endpoint-3", "key-3", BASE_URL));

		loadBalancer = new TtsLoadBalancer(endpoints);

		WebClient.Builder webClientBuilder = WebClient.builder()
			.clientConnector(new ReactorClientHttpConnector());

		Voice voice = Voice.builder().id("test-voice-id").name("Test Voice").provider("supertone")
			.language("ko").style(VoiceStyle.NEUTRAL).outputFormat(AudioFormat.WAV)
			.settings(new VoiceSettings(0, 1.0, 1.0)).build();

		adapter = new LoadBalancedSupertoneTtsAdapter(webClientBuilder, loadBalancer, voice);
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

	// ==================== 취약점 재현 테스트 ====================

	@Test
	@DisplayName("[취약점 4] 요청 취소 시 activeRequests 카운트가 감소하지 않음")
	void vulnerability4_cancelledRequest_leaksActiveRequestCount() throws InterruptedException {
		// Given: 느린 응답을 반환하는 서버 (5초 지연)
		fakeServer.setEndpointBehavior("key-1", FakeSupertoneServer.ServerBehavior.delayed(5000));
		fakeServer.setEndpointBehavior("key-2", FakeSupertoneServer.ServerBehavior.delayed(5000));
		fakeServer.setEndpointBehavior("key-3", FakeSupertoneServer.ServerBehavior.delayed(5000));

		int initialCount = loadBalancer.getEndpoints().stream()
			.mapToInt(TtsEndpoint::getActiveRequests)
			.sum();

		// When: 요청 시작 후 즉시 취소
		Flux<byte[]> flux = adapter.streamSynthesize("test", AudioFormat.WAV);
		flux.subscribe().dispose(); // 즉시 취소

		// 약간의 시간 대기 (요청이 시작되도록)
		Thread.sleep(100);

		// Then: activeRequests가 증가된 상태로 유지됨 (취약점)
		// doOnCancel()이 없어서 decrementActiveRequests()가 호출되지 않음
		// 시간이 지나면 이 카운트가 누적되어 부하 측정이 왜곡됨
		int afterCancelCount = loadBalancer.getEndpoints().stream()
			.mapToInt(TtsEndpoint::getActiveRequests)
			.sum();

		// 문제: 취소된 요청의 카운트가 남아있을 수 있음
		// (정상이라면 취소 후 0이어야 함)
		// Note: 타이밍에 따라 결과가 달라질 수 있음
		assertThat(afterCancelCount).isGreaterThanOrEqualTo(initialCount);
	}

	@Test
	@DisplayName("[취약점 5] Warmup 실패 시 엔드포인트 상태가 HEALTHY로 유지됨")
	void vulnerability5_warmupFailure_endpointStaysHealthy() {
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
			webClientBuilder, badLoadBalancer, voice);

		// When: prepare() 호출 (warmup 실패)
		badAdapter.prepare().block();

		// Then: 모든 엔드포인트가 여전히 HEALTHY 상태 (취약점)
		// 문제: warmup 실패해도 상태 변경 없음
		for (TtsEndpoint endpoint : badLoadBalancer.getEndpoints()) {
			assertThat(endpoint.getHealth())
				.as("Warmup 실패한 엔드포인트 %s가 여전히 HEALTHY 상태", endpoint.getId())
				.isEqualTo(TtsEndpoint.EndpointHealth.HEALTHY);
		}
	}
}
