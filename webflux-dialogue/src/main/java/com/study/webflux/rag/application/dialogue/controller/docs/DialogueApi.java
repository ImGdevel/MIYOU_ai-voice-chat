package com.study.webflux.rag.application.dialogue.controller.docs;

import com.study.webflux.rag.application.dialogue.dto.CreateSessionRequest;
import com.study.webflux.rag.application.dialogue.dto.CreateSessionResponse;
import com.study.webflux.rag.application.dialogue.dto.RagDialogueRequest;
import com.study.webflux.rag.application.dialogue.dto.SttDialogueResponse;
import com.study.webflux.rag.application.dialogue.dto.SttTranscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(
	name = "대화 API",
	description = "LLM 및 TTS를 활용한 RAG 대화 파이프라인"
)
public interface DialogueApi {

	@Operation(
		summary = "새 대화 세션 생성",
		description = "사용자와 페르소나로 새로운 대화 세션을 생성합니다"
	)
	@ApiResponse(
		responseCode = "200",
		description = "세션 생성 성공",
		content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
	)
	Mono<CreateSessionResponse> createSession(
		@Valid CreateSessionRequest request
	);

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
		@Parameter(description = "오디오 포맷 (wav 또는 mp3)", example = "mp3") @RequestParam(defaultValue = "wav") String format,
		ServerHttpResponse response
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

	@Operation(
		summary = "음성 파일 전사(STT)",
		description = "업로드한 음성 파일을 Whisper 기반 STT로 텍스트로 변환합니다"
	)
	@ApiResponse(
		responseCode = "200",
		description = "전사 성공",
		content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
	)
	Mono<SttTranscriptionResponse> ragDialogueStt(
		@Parameter(description = "전사할 오디오 파일") @RequestPart("audio") FilePart audioFile,
		@Parameter(description = "언어 코드(예: ko, en). 생략 시 기본 언어 사용", example = "ko") @RequestParam(required = false) String language
	);

	@Operation(
		summary = "음성 파일 기반 대화 응답",
		description = "음성 파일을 STT로 텍스트 변환한 뒤 해당 텍스트로 대화 응답을 생성합니다"
	)
	@ApiResponse(
		responseCode = "200",
		description = "전사 및 응답 생성 성공",
		content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
	)
	Mono<SttDialogueResponse> ragDialogueSttText(
		@Parameter(description = "전사할 오디오 파일") @RequestPart("audio") FilePart audioFile,
		@Parameter(description = "언어 코드(예: ko, en). 생략 시 기본 언어 사용", example = "ko") @RequestParam(required = false) String language,
		@Parameter(description = "세션 ID", example = "550e8400-e29b-41d4-a716-446655440000") @RequestParam @jakarta.validation.constraints.NotBlank String sessionId
	);
}
