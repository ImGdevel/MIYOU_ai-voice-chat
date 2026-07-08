package com.miyou.app.api.dialogue.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "세션 생성 요청 DTO")
data class CreateSessionRequest(
    @field:NotBlank(message = "userId is required")
    @field:Size(max = 128, message = "userId too long")
    @field:Schema(description = "대화 세션을 소유할 사용자 ID", example = "user-123")
    val userId: String = "",
    // PersonaId(빈값/null 허용) 검증 동기화. Jackson 역직렬화 예외 방지용 String? 타입 적용.
    @field:Size(max = 64, message = "personaId too long")
    @field:Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "personaId contains invalid characters")
    @field:Schema(description = "대화 상대방 퍼소나 ID (지정되지 않거나 null인 경우 기본 AI 비서로 지정됨)", example = "persona_gentle_bob")
    val personaId: String? = null,
)
