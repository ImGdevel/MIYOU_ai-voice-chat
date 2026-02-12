package com.study.webflux.rag.application.dialogue.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.study.webflux.rag.application.dialogue.controller.docs.DialogueApi;
import com.study.webflux.rag.application.dialogue.dto.RagDialogueRequest;
import com.study.webflux.rag.domain.dialogue.port.DialoguePipelineUseCase;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

/** RAG 대화 파이프라인 REST 엔드포인트. */
@Validated
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/rag/dialogue")
public class DialogueController implements DialogueApi {

	private final DialoguePipelineUseCase dialoguePipelineUseCase;
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

		return dialoguePipelineUseCase.executeAudioStreaming(request.text(), targetFormat)
			.map(bufferFactory::wrap);
	}

	/** 텍스트 전용 RAG 파이프라인을 SSE로 스트리밍. */
	@PostMapping(path = "/text", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> ragDialogueText(
		@Valid @RequestBody RagDialogueRequest request) {
		return dialoguePipelineUseCase.executeTextOnly(request.text());
	}
}
