package com.miyou.app.api.dialogue.docs

import com.miyou.app.api.dialogue.dto.CreateSessionRequest
import com.miyou.app.api.dialogue.dto.CreateSessionResponse
import com.miyou.app.api.dialogue.dto.RagDialogueRequest
import com.miyou.app.api.dialogue.dto.SttTranscriptionResponse
import com.miyou.app.domain.auth.model.AuthenticatedUser
import com.miyou.app.exception.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "세션 생성 성공",
                content = [Content(schema = Schema(implementation = CreateSessionResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 파라미터 (userId 누락, personaId 포맷 위반 등)",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패 또는 권한 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun createSession(
        @Valid request: CreateSessionRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Mono<CreateSessionResponse>

    @Operation(
        summary = "오디오 응답 요청",
        description = "RAG 대화 요청을 받아 TTS가 합성된 오디오 스트림을 반환합니다(지원 형식: wav, mp3)",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "오디오 스트림 응답",
                content = [
                    Content(mediaType = "audio/wav"),
                    Content(mediaType = "audio/mpeg"),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 오디오 형식 또는 파라미터 규격 오류",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "402",
                description = "결제 필요 (크레딧 잔액 부족)",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "대화 세션을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류 (TTS 합성 실패 등)",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
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
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "텍스트 응답 스트림",
                content = [Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)],
            ),
            ApiResponse(
                responseCode = "400",
                description = "규격 어긋난 요청 파라미터",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "402",
                description = "결제 필요 (크레딧 잔액 부족)",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "대화 세션을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun ragDialogueText(
        @Valid request: RagDialogueRequest,
    ): Flux<String>

    @Operation(
        summary = "오디오 STT",
        description = "사용자 음성 파일을 Whisper 기반 STT로 텍스트로 변환합니다",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "STT 변환 성공",
                content = [Content(schema = Schema(implementation = SttTranscriptionResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 오디오 파일 헤더 또는 누락된 파일명",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Whisper STT 처리 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun ragDialogueStt(
        @Parameter(description = "오디오 파일") @RequestPart("audio") audioFile: FilePart,
        @Parameter(description = "언어 코드(예: ko, en). 미지정 시 기본값 사용", example = "ko")
        @RequestParam(required = false) language: String?,
    ): Mono<SttTranscriptionResponse>
}
