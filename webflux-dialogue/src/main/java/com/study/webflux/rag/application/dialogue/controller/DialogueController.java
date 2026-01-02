package com.study.webflux.rag.application.dialogue.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.study.webflux.rag.application.dialogue.controller.docs.DialogueApi;
import com.study.webflux.rag.application.dialogue.dto.RagDialogueRequest;
import com.study.webflux.rag.domain.dialogue.port.DialoguePipelineUseCase;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

@Validated
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/rag/dialogue")
public class DialogueController implements DialogueApi {

	private final DialoguePipelineUseCase dialoguePipelineUseCase;
	private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

	@PostMapping(path = "/audio", produces = {"audio/wav", "audio/mpeg"})
	public Flux<DataBuffer> ragDialogueAudio(
		@Valid @RequestBody RagDialogueRequest request,
		@RequestParam(defaultValue = "wav") String format) {
		AudioFormat targetFormat = AudioFormat.fromString(format);
		log.info("오디오 스트리밍 요청: format={}", targetFormat);
		return dialoguePipelineUseCase.executeAudioStreaming(request.text(), targetFormat)
			.map(bufferFactory::wrap);
	}

	@PostMapping(path = "/text", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> ragDialogueText(
		@Valid @RequestBody RagDialogueRequest request) {
		return dialoguePipelineUseCase.executeTextOnly(request.text());
	}
}
