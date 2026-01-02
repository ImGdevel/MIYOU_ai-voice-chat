package com.study.webflux.rag.application.dialogue.controller.docs;

import com.study.webflux.rag.application.dialogue.dto.RagDialogueRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;

@Tag(
	name = "대화 API",
	description = "LLM 및 TTS를 활용한 RAG 대화 파이프라인"
)
public interface DialogueApi {

	@Operation(
		summary = "오디오 스트리밍 응답",
		description = "RAG 대화 응답을 TTS로 변환한 오디오를 스트리밍 방식으로 반환합니다 (기본 WAV, ?format=mp3)"
	)
	@ApiResponse(
		responseCode = "200",
		description = "스트리밍 오디오",
		content = {
			@Content(mediaType = "audio/wav"),
			@Content(mediaType = "audio/mpeg")
		}
	)
	Flux<DataBuffer> ragDialogueAudio(
		@Valid RagDialogueRequest request,
		@Parameter(description = "오디오 포맷 (wav 또는 mp3)", example = "mp3") @RequestParam(defaultValue = "wav") String format
	);

	@Operation(
		summary = "텍스트 전용 스트리밍 응답",
		description = "오디오 변환 없이 LLM 텍스트 응답만 스트리밍 방식으로 반환합니다"
	)
	@ApiResponse(
		responseCode = "200",
		description = "스트리밍 텍스트",
		content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)
	)
	Flux<String> ragDialogueText(
		@Valid RagDialogueRequest request
	);
}
