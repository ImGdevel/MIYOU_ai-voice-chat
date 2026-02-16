package com.study.webflux.rag.application.dialogue.controller;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.study.webflux.rag.application.dialogue.dto.RagDialogueRequest;
import com.study.webflux.rag.application.dialogue.service.DialogueSpeechService;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.dialogue.port.DialoguePipelineUseCase;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(DialogueController.class)
class DialogueControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockitoBean
	private DialoguePipelineUseCase dialoguePipelineUseCase;

	@MockitoBean
	private DialogueSpeechService dialogueSpeechService;

	@Test
	@DisplayName("텍스트 질의 요청 시 스트리밍 응답을 반환한다")
	void ragDialogueText_shouldReturnStream() {
		String userId = "user-1";
		String testText = "Hello world";
		RagDialogueRequest request = new RagDialogueRequest(userId, testText, Instant.now());

		when(dialoguePipelineUseCase.executeTextOnly(eq(UserId.of(userId)), eq(testText)))
			.thenReturn(Flux.just("token1", "token2"));

		webTestClient.post().uri("/rag/dialogue/text").contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request).exchange().expectStatus().isOk();

		verify(dialoguePipelineUseCase).executeTextOnly(UserId.of(userId), testText);
	}

	@Test
	@DisplayName("오디오 요청 시 기본값으로 WAV 형식을 반환한다")
	void ragDialogueAudio_shouldDelegateToWav() {
		String userId = "user-1";
		String testText = "Default audio";
		RagDialogueRequest request = new RagDialogueRequest(userId, testText, Instant.now());

		byte[] audioBytes = "default-audio".getBytes();

		when(dialoguePipelineUseCase.executeAudioStreaming(eq(UserId.of(userId)),
			eq(testText),
			eq(AudioFormat.WAV)))
			.thenReturn(Flux.just(audioBytes));

		webTestClient.post().uri("/rag/dialogue/audio").contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.parseMediaType("audio/wav"))
			.bodyValue(request).exchange().expectStatus().isOk().expectHeader()
			.contentType("audio/wav");

		verify(dialoguePipelineUseCase).executeAudioStreaming(UserId.of(userId),
			testText,
			AudioFormat.WAV);
	}

	@Test
	@DisplayName("MP3 형식 요청 시 MP3 오디오를 반환한다")
	void ragDialogueAudio_withMp3Format_shouldReturnMp3() {
		String userId = "user-1";
		String testText = "MP3 request";
		RagDialogueRequest request = new RagDialogueRequest(userId, testText, Instant.now());

		byte[] audioBytes = "mp3-audio-data".getBytes();

		when(dialoguePipelineUseCase.executeAudioStreaming(eq(UserId.of(userId)),
			eq(testText),
			eq(AudioFormat.MP3)))
			.thenReturn(Flux.just(audioBytes));

		webTestClient.post().uri(uriBuilder -> uriBuilder.path("/rag/dialogue/audio")
			.queryParam("format", "mp3").build())
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.parseMediaType("audio/mpeg"))
			.bodyValue(request).exchange()
			.expectStatus().isOk()
			.expectHeader().contentType("audio/mpeg");

		verify(dialoguePipelineUseCase).executeAudioStreaming(UserId.of(userId),
			testText,
			AudioFormat.MP3);
	}

	@Test
	@DisplayName("빈 텍스트 요청 시 400 Bad Request를 반환한다")
	void ragDialogueText_withBlankText_shouldReturnBadRequest() {
		RagDialogueRequest request = new RagDialogueRequest("user-1", "", Instant.now());

		webTestClient.post().uri("/rag/dialogue/text").contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request).exchange().expectStatus().isBadRequest();
	}

	@Test
	@DisplayName("타임스탬프 없는 요청 시 400 Bad Request를 반환한다")
	void ragDialogueText_withNullTimestamp_shouldReturnBadRequest() {
		RagDialogueRequest request = new RagDialogueRequest("user-1", "test", null);

		webTestClient.post().uri("/rag/dialogue/text").contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request).exchange().expectStatus().isBadRequest();
	}
}
