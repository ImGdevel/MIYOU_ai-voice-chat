package com.study.webflux.rag.application.dialogue.controller;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.study.webflux.rag.application.dialogue.dto.RagDialogueRequest;
import com.study.webflux.rag.domain.dialogue.port.DialoguePipelineUseCase;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
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

	@Test
	void ragDialogueText_shouldReturnStream() {
		String testText = "Hello world";
		RagDialogueRequest request = new RagDialogueRequest(testText, Instant.now());

		when(dialoguePipelineUseCase.executeTextOnly(eq(testText)))
			.thenReturn(Flux.just("token1", "token2"));

		webTestClient.post().uri("/rag/dialogue/text").contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request).exchange().expectStatus().isOk();

		verify(dialoguePipelineUseCase).executeTextOnly(testText);
	}

	@Test
	void ragDialogueAudio_shouldDelegateToWav() {
		String testText = "Default audio";
		RagDialogueRequest request = new RagDialogueRequest(testText, Instant.now());

		byte[] audioBytes = "default-audio".getBytes();

		when(dialoguePipelineUseCase.executeAudioStreaming(eq(testText), eq(AudioFormat.WAV)))
			.thenReturn(Flux.just(audioBytes));

		webTestClient.post().uri("/rag/dialogue/audio").contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request).exchange().expectStatus().isOk().expectHeader()
			.contentType("audio/wav");

		verify(dialoguePipelineUseCase).executeAudioStreaming(testText, AudioFormat.WAV);
	}

	@Test
	void ragDialogueAudio_withMp3Format_shouldReturnMp3() {
		String testText = "MP3 request";
		RagDialogueRequest request = new RagDialogueRequest(testText, Instant.now());

		byte[] audioBytes = "mp3-audio-data".getBytes();

		when(dialoguePipelineUseCase.executeAudioStreaming(eq(testText), eq(AudioFormat.MP3)))
			.thenReturn(Flux.just(audioBytes));

		webTestClient.post().uri(uriBuilder -> uriBuilder.path("/rag/dialogue/audio")
			.queryParam("format", "mp3").build())
			.contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange()
			.expectStatus().isOk().expectHeader().contentType("audio/mpeg");

		verify(dialoguePipelineUseCase).executeAudioStreaming(testText, AudioFormat.MP3);
	}

	@Test
	void ragDialogueText_withBlankText_shouldReturnBadRequest() {
		RagDialogueRequest request = new RagDialogueRequest("", Instant.now());

		webTestClient.post().uri("/rag/dialogue/text").contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request).exchange().expectStatus().isBadRequest();
	}

	@Test
	void ragDialogueText_withNullTimestamp_shouldReturnBadRequest() {
		RagDialogueRequest request = new RagDialogueRequest("test", null);

		webTestClient.post().uri("/rag/dialogue/text").contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request).exchange().expectStatus().isBadRequest();
	}
}
