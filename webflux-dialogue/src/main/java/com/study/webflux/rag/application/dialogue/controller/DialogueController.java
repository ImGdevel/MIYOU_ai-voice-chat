package com.study.webflux.rag.application.dialogue.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.study.webflux.rag.application.dialogue.controller.docs.DialogueApi;
import com.study.webflux.rag.application.dialogue.dto.RagDialogueRequest;
import com.study.webflux.rag.domain.dialogue.port.DialoguePipelineUseCase;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/rag/dialogue")
public class DialogueController implements DialogueApi {

	private final DialoguePipelineUseCase dialoguePipelineUseCase;
	private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

	@PostMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> ragDialogueStream(
		@Valid @RequestBody RagDialogueRequest request) {
		return dialoguePipelineUseCase.executeStreaming(request.text());
	}

	@PostMapping(path = "/audio/wav", produces = "audio/wav")
	public Flux<DataBuffer> ragDialogueAudioWav(
		@Valid @RequestBody RagDialogueRequest request) {
		return dialoguePipelineUseCase.executeAudioStreaming(request.text())
			.map(bufferFactory::wrap);
	}

	@PostMapping(path = "/audio/mp3", produces = "audio/mpeg")
	public Flux<DataBuffer> ragDialogueAudioMp3(
		@Valid @RequestBody RagDialogueRequest request) {
		return dialoguePipelineUseCase.executeAudioStreaming(request.text())
			.map(bufferFactory::wrap);
	}

	@PostMapping(path = "/audio", produces = "audio/wav")
	public Flux<DataBuffer> ragDialogueAudio(
		@Valid @RequestBody RagDialogueRequest request) {
		return ragDialogueAudioWav(request);
	}

	@PostMapping(path = "/text", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> ragDialogueText(
		@Valid @RequestBody RagDialogueRequest request) {
		return dialoguePipelineUseCase.executeTextOnly(request.text());
	}
}
