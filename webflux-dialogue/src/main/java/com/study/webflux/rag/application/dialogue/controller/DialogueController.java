package com.study.webflux.rag.application.dialogue.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.study.webflux.rag.application.dialogue.controller.docs.DialogueApi;
import com.study.webflux.rag.application.dialogue.dto.RagDialogueRequest;
import com.study.webflux.rag.application.dialogue.dto.SttDialogueResponse;
import com.study.webflux.rag.application.dialogue.dto.SttTranscriptionResponse;
import com.study.webflux.rag.application.dialogue.service.DialogueSpeechService;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.dialogue.port.DialoguePipelineUseCase;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** RAG 대화 파이프라인 REST 엔드포인트. */
@Validated
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/rag/dialogue")
public class DialogueController implements DialogueApi {

	private final DialoguePipelineUseCase dialoguePipelineUseCase;
	private final DialogueSpeechService dialogueSpeechService;
	private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

	/** TTS 포함 RAG 파이프라인을 실행하고 요청 포맷으로 오디오를 스트리밍. */
	@PostMapping(path = "/audio", produces = {"audio/wav", "audio/mpeg"})
	public Flux<DataBuffer> ragDialogueAudio(
		@Valid @RequestBody RagDialogueRequest request,
		@RequestParam(defaultValue = "wav") String format,
		ServerHttpResponse response) {
		AudioFormat targetFormat;
		try {
			targetFormat = AudioFormat.fromString(format);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"지원하지 않는 오디오 포맷입니다: " + format, e);
		}

		response.getHeaders().setContentType(MediaType.valueOf(targetFormat.getMediaType()));

		UserId userId = UserId.of(request.userId());
		return dialoguePipelineUseCase.executeAudioStreaming(userId, request.text(), targetFormat)
			.map(bufferFactory::wrap);
	}

	/** 텍스트 전용 RAG 파이프라인을 SSE로 스트리밍. */
	@PostMapping(path = "/text", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> ragDialogueText(
		@Valid @RequestBody RagDialogueRequest request) {
		UserId userId = UserId.of(request.userId());
		return dialoguePipelineUseCase.executeTextOnly(userId, request.text());
	}

	/** 업로드한 음성 파일을 Whisper STT로 텍스트 변환합니다. */
	@PostMapping(path = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<SttTranscriptionResponse> ragDialogueStt(
		@RequestPart("audio") FilePart audioFile,
		@RequestParam(required = false) String language) {
		return dialogueSpeechService.transcribe(audioFile, language)
			.map(SttTranscriptionResponse::new);
	}

	/** 음성 파일을 텍스트로 변환 후 대화 응답을 생성합니다. */
	@PostMapping(path = "/stt/text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<SttDialogueResponse> ragDialogueSttText(
		@RequestPart("audio") FilePart audioFile,
		@RequestParam(required = false) String language,
		@RequestParam(required = false) String userId) {
		UserId targetUserId = (userId == null || userId.isBlank())
			? UserId.generate()
			: UserId.of(
				userId);
		return dialogueSpeechService.transcribeAndRespond(targetUserId, audioFile, language)
			.map(result -> new SttDialogueResponse(result.transcription(), result.response()));
	}
}
