package com.miyou.app.infrastructure.inbound.web.dialogue.docs

import com.miyou.app.infrastructure.inbound.web.dialogue.dto.CreateSessionRequest
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.CreateSessionResponse
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.RagDialogueRequest
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.SttDialogueResponse
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.SttTranscriptionResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Tag(name = "대화 API", description = "LLM/TTS와 RAG 대화를 위한 REST API")
interface DialogueApi {
    @Operation(
        summary = "대화 세션 생성",
        description = "사용자 또는 퍼소나 정보를 기반으로 새 세션을 생성합니다",
    )
    @ApiResponse(
        responseCode = "200",
        description = "세션 생성 결과",
        content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)],
    )
    fun createSession(
        @Valid request: CreateSessionRequest,
    ): Mono<CreateSessionResponse>

    @Operation(
        summary = "오디오 응답 요청",
        description = "RAG 대화 요청을 받아 TTS가 합성된 오디오 스트림을 반환합니다(지원 형식: wav, mp3)",
    )
    @ApiResponse(
        responseCode = "200",
        description = "오디오 스트림 응답",
        content = [
            Content(mediaType = "audio/wav"),
            Content(mediaType = "audio/mpeg"),
        ],
    )
    fun ragDialogueAudio(
        @Valid request: RagDialogueRequest,
        @Parameter(description = "오디오 포맷 (wav 또는 mp3)", example = "mp3")
        @RequestParam(defaultValue = "wav") format: String,
        response: ServerHttpResponse,
    ): Flux<DataBuffer>

    @Operation(
        summary = "텍스트 응답 요청",
        description = "텍스트 기반 RAG 대화를 SSE 형식으로 스트리밍합니다",
    )
    @ApiResponse(
        responseCode = "200",
        description = "텍스트 응답 스트림",
        content = [Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)],
    )
    fun ragDialogueText(
        @Valid request: RagDialogueRequest,
    ): Flux<String>

    @Operation(
        summary = "오디오 STT",
        description = "사용자 음성 파일을 Whisper 기반 STT로 텍스트로 변환합니다",
    )
    @ApiResponse(
        responseCode = "200",
        description = "STT 변환 결과",
        content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)],
    )
    fun ragDialogueStt(
        @Parameter(description = "오디오 파일") @RequestPart("audio") audioFile: FilePart,
        @Parameter(description = "언어 코드(예: ko, en). 미지정 시 기본값 사용", example = "ko")
        @RequestParam(required = false) language: String?,
    ): Mono<SttTranscriptionResponse>

    @Operation(
        summary = "오디오 STT + 텍스트 응답",
        description = "사용자 음성 파일을 STT로 변환 후 대화 파이프라인 결과를 응답으로 반환합니다",
    )
    @ApiResponse(
        responseCode = "200",
        description = "STT+응답 결과",
        content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)],
    )
    fun ragDialogueSttText(
        @Parameter(description = "오디오 파일") @RequestPart("audio") audioFile: FilePart,
        @Parameter(description = "언어 코드(예: ko, en). 미지정 시 기본값 사용", example = "ko")
        @RequestParam(required = false) language: String?,
        @Parameter(description = "세션 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @RequestParam
        @NotBlank
        sessionId: String,
    ): Mono<SttDialogueResponse>
}
