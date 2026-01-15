package com.study.webflux.rag.infrastructure.dialogue.adapter.stt;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.study.webflux.rag.domain.dialogue.model.AudioTranscriptionInput;
import com.study.webflux.rag.domain.dialogue.port.SttPort;
import reactor.core.publisher.Mono;

/** OpenAI Whisper API 기반 STT 어댑터입니다. */
@Slf4j
public class OpenAiWhisperSttAdapter implements SttPort {

	private final WebClient webClient;
	private final String model;

	public OpenAiWhisperSttAdapter(WebClient.Builder webClientBuilder,
		String apiKey,
		String baseUrl,
		String model) {
		this.model = model;
		this.webClient = webClientBuilder
			.baseUrl(normalizeBaseUrl(baseUrl))
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.build();
	}

	@Override
	public Mono<String> transcribe(AudioTranscriptionInput input) {
		log.info("STT 변환 요청 - fileName: {}, contentType: {}, size: {} bytes",
			input.fileName(),
			input.contentType(),
			input.audioBytes().length);

		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("model", model);
		if (input.language() != null && !input.language().isBlank()) {
			builder.part("language", input.language());
		}
		builder.part("response_format", "json");

		ByteArrayResource audioResource = new ByteArrayResource(input.audioBytes()) {
			@Override
			public String getFilename() {
				return input.fileName();
			}
		};

		builder.part("file", audioResource)
			.contentType(MediaType.parseMediaType(input.contentType()));

		return webClient.post()
			.uri("/audio/transcriptions")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(BodyInserters.fromMultipartData(builder.build()))
			.retrieve()
			.onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
				response -> response.bodyToMono(String.class)
					.doOnNext(body -> log.error("OpenAI API 에러 - status: {}, body: {}",
						response.statusCode(),
						body))
					.then(Mono.error(new RuntimeException(
						"OpenAI API 에러: " + response.statusCode()))))
			.bodyToMono(OpenAiTranscriptionResponse.class)
			.map(OpenAiTranscriptionResponse::text)
			.doOnSuccess(
				text -> log.info("STT 변환 완료: {} chars", text != null ? text.length() : 0));
	}

	private String normalizeBaseUrl(String baseUrl) {
		String normalized = baseUrl == null || baseUrl.isBlank()
			? "https://api.openai.com"
			: baseUrl.trim();

		if (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		if (normalized.endsWith("/v1")) {
			return normalized;
		}
		return normalized + "/v1";
	}

	private record OpenAiTranscriptionResponse(
		String text) {
	}
}
