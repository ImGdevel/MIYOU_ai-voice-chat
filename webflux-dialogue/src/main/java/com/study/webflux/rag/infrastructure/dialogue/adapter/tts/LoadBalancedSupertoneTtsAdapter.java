package com.study.webflux.rag.infrastructure.dialogue.adapter.tts;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.study.webflux.rag.domain.dialogue.port.TtsPort;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import com.study.webflux.rag.domain.voice.model.Voice;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsErrorClassifier;
import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Supertone TTS 어댑터 - 로드 밸런싱 지원
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadBalancedSupertoneTtsAdapter implements TtsPort {

	private final WebClient.Builder webClientBuilder;
	private final TtsLoadBalancer loadBalancer;
	private final Voice voice;
	private final Map<String, WebClient> webClientCache = new ConcurrentHashMap<>();

	@Override
	public Flux<byte[]> streamSynthesize(String text, AudioFormat format) {
		return streamSynthesizeWithRetry(text, format, 0);
	}

	private Flux<byte[]> streamSynthesizeWithRetry(String text,
		AudioFormat format,
		int attemptCount) {
		if (attemptCount >= 2) {
			return Flux.error(new RuntimeException("모든 TTS 엔드포인트 요청 실패: 최대 재시도 횟수 초과"));
		}

		TtsEndpoint endpoint = loadBalancer.selectEndpoint();
		endpoint.incrementActiveRequests();

		log.debug("엔드포인트 {} 선택, 활성 요청 수: {}, 시도 횟수: {}",
			endpoint.getId(),
			endpoint.getActiveRequests(),
			attemptCount + 1);

		return synthesizeWithEndpoint(endpoint, text, format).doOnComplete(() -> {
			endpoint.decrementActiveRequests();
			loadBalancer.reportSuccess(endpoint);
		}).onErrorResume(error -> {
			endpoint.decrementActiveRequests();
			loadBalancer.reportFailure(endpoint, error);

			TtsEndpoint.FailureType failureType = TtsErrorClassifier.classifyError(error);

			if (failureType == TtsEndpoint.FailureType.CLIENT_ERROR) {
				log.error("클라이언트 에러 발생, 재시도 없이 즉시 실패: {}", error.getMessage());
				return Flux.error(error);
			}

			log.warn("엔드포인트 {} 장애로 다른 엔드포인트로 재시도 ({}회차)", endpoint.getId(), attemptCount + 2);
			return streamSynthesizeWithRetry(text, format, attemptCount + 1);
		});
	}

	private Flux<byte[]> synthesizeWithEndpoint(TtsEndpoint endpoint,
		String text,
		AudioFormat format) {
		AudioFormat outputFormat = format != null ? format : voice.getOutputFormat();
		var settings = voice.getSettings();
		var voiceSettings = Map.of("pitch_shift",
			settings.pitchShift(),
			"pitch_variance",
			settings.pitchVariance(),
			"speed",
			settings.speed());

		var payload = new HashMap<String, Object>();
		payload.put("text", text);
		payload.put("language", voice.getLanguage());
		payload.put("style", voice.getStyle().getValue());
		payload.put("output_format", outputFormat.name().toLowerCase());
		payload.put("voice_settings", voiceSettings);
		payload.put("include_phonemes", false);

		WebClient webClient = getOrCreateWebClient(endpoint);

		return webClient.post().uri("/v1/text-to-speech/{voice_id}/stream", voice.getId())
			.contentType(MediaType.APPLICATION_JSON).bodyValue(payload)
			.accept(MediaType.parseMediaType(outputFormat.getMediaType())).retrieve()
			.bodyToFlux(DataBuffer.class).timeout(Duration.ofSeconds(10)).map(dataBuffer -> {
				byte[] bytes = new byte[dataBuffer.readableByteCount()];
				dataBuffer.read(bytes);
				org.springframework.core.io.buffer.DataBufferUtils.release(dataBuffer);
				return bytes;
			});
	}

	private WebClient getOrCreateWebClient(TtsEndpoint endpoint) {
		return webClientCache.computeIfAbsent(endpoint.getId(),
			id -> webClientBuilder.baseUrl(endpoint.getBaseUrl())
				.defaultHeader("x-sup-api-key", endpoint.getApiKey()).build());
	}

	@Override
	public Mono<byte[]> synthesize(String text, AudioFormat format) {
		return streamSynthesize(text, format).collectList().map(byteArrays -> {
			int totalSize = byteArrays.stream().mapToInt(arr -> arr.length).sum();
			byte[] result = new byte[totalSize];
			int offset = 0;
			for (byte[] arr : byteArrays) {
				System.arraycopy(arr, 0, result, offset, arr.length);
				offset += arr.length;
			}
			return result;
		});
	}

	@Override
	public Mono<Void> prepare() {
		return Flux.fromIterable(loadBalancer.getEndpoints())
			.flatMap(endpoint -> warmupEndpoint(endpoint)
				.doOnError(error -> log.warn("앤드포인트 준비에 실패했습니다. : {}", endpoint.getId(), error))
				.onErrorResume(error -> Mono.empty()))
			.then();
	}

	private Mono<Void> warmupEndpoint(TtsEndpoint endpoint) {
		WebClient webClient = getOrCreateWebClient(endpoint);

		return webClient.get().uri("/v1/credits").retrieve().toBodilessEntity()
			.timeout(Duration.ofSeconds(2)).then();
	}
}
