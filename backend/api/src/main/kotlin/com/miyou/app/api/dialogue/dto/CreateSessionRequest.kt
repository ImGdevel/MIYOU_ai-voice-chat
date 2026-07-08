package com.miyou.app.api.dialogue.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateSessionRequest(
    @field:NotBlank(message = "userId is required")
    @field:Size(max = 128, message = "userId too long")
    val userId: String = "",
    // 빈 문자열은 PersonaId.ofNullable()에서 기본 페르소나로 처리되므로 허용해야 한다
    // (그래서 정규식이 `+`가 아니라 `*`). PersonaId의 require(length<=64, 정규식)와
    // 반드시 동기화 상태를 유지한다.
    @field:Size(max = 64, message = "personaId too long")
    @field:Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "personaId contains invalid characters")
    val personaId: String = "",
)
