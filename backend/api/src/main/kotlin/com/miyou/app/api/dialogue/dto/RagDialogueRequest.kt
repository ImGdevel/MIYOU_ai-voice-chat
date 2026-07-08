package com.miyou.app.api.dialogue.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(description = "RAG 대화 요청")
data class RagDialogueRequest(
    // ConversationSessionId의 require(value.length <= 128)와 반드시 동기화 상태를 유지한다.
    // 안 맞추면 도메인 require()가 400 대신 500으로 새는 경계 검증 갭이 다시 생긴다.
    @field:Schema(description = "세션 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @field:NotBlank
    @field:Size(max = 128, message = "sessionId too long")
    val sessionId: String,
    @field:Schema(description = "요청 텍스트", example = "안녕하세요")
    @field:NotBlank
    val text: String,
    @field:Schema(description = "요청 시각", example = "2024-12-21T12:00:00Z")
    @field:NotNull
    val requestedAt: Instant,
)
