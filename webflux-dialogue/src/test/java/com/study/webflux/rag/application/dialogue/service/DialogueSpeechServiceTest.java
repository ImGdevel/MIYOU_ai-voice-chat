package com.study.webflux.rag.application.dialogue.service;

import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;

import com.study.webflux.rag.domain.dialogue.model.ConversationSession;
import com.study.webflux.rag.domain.dialogue.port.DialoguePipelineUseCase;
import com.study.webflux.rag.domain.dialogue.port.SttPort;
import com.study.webflux.rag.fixture.ConversationSessionFixture;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogueSpeechServiceTest {

	@Test
	@DisplayName("음성 파일을 전사한 뒤 텍스트 응답까지 생성한다")
	void transcribeAndRespond_shouldReturnTranscriptionAndResponse() {
		SttPort sttPort = mock(SttPort.class);
		DialoguePipelineUseCase dialoguePipelineUseCase = mock(DialoguePipelineUseCase.class);
		DialogueSpeechService service = new DialogueSpeechService(sttPort, dialoguePipelineUseCase,
			new RagDialogueProperties());
		FilePart audioFile = createFilePart("voice.mp3",
			MediaType.parseMediaType("audio/mpeg"),
			"dummy-audio".getBytes());

		ConversationSession session = ConversationSessionFixture.create();

		when(sttPort.transcribe(any())).thenReturn(Mono.just("회의 일정 알려줘"));
		when(dialoguePipelineUseCase.executeTextOnly(any(), any()))
			.thenReturn(Flux.just("오늘 회의는 ", "오후 2시입니다."));

		Mono<DialogueSpeechService.SpeechDialogueResult> result = service.transcribeAndRespond(
			session,
			audioFile,
			null);

		StepVerifier.create(result)
			.expectNextMatches(response -> "회의 일정 알려줘".equals(response.transcription())
				&& "오늘 회의는 오후 2시입니다.".equals(response.response()))
			.verifyComplete();
	}

	@Test
	@DisplayName("오디오가 아닌 파일 업로드는 400 예외를 반환한다")
	void transcribe_shouldRejectNonAudioFile() {
		SttPort sttPort = mock(SttPort.class);
		DialoguePipelineUseCase dialoguePipelineUseCase = mock(DialoguePipelineUseCase.class);
		DialogueSpeechService service = new DialogueSpeechService(sttPort, dialoguePipelineUseCase,
			new RagDialogueProperties());
		FilePart textFile = createFilePart("test.txt", MediaType.TEXT_PLAIN, "text".getBytes());

		StepVerifier.create(service.transcribe(textFile, "ko"))
			.expectErrorSatisfies(error -> {
				if (!(error instanceof ResponseStatusException exception)) {
					throw new AssertionError("ResponseStatusException이 아닙니다");
				}
				if (!exception.getStatusCode().is4xxClientError()) {
					throw new AssertionError("4xx 에러가 아닙니다");
				}
			})
			.verify();
	}

	private FilePart createFilePart(String filename, MediaType contentType, byte[] bytes) {
		FilePart filePart = mock(FilePart.class);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);

		when(filePart.filename()).thenReturn(filename);
		when(filePart.headers()).thenReturn(headers);
		when(filePart.content()).thenReturn(
			Flux.just(new DefaultDataBufferFactory().wrap(bytes)));
		return filePart;
	}
}
