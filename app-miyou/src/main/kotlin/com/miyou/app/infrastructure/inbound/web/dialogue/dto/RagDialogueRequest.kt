package com.miyou.app.infrastructure.inbound.web.dialogue.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(description = "RAG 대화 요청")
data class RagDialogueRequest(
    @field:Schema(description = "세션 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @field:NotBlank
    val sessionId: String,
    @field:Schema(description = "요청 텍스트", example = "안녕하세요")
    @field:NotBlank
    val text: String,
    @field:Schema(description = "요청 시각", example = "2024-12-21T12:00:00Z")
    @field:NotNull
    val requestedAt: Instant,
)
