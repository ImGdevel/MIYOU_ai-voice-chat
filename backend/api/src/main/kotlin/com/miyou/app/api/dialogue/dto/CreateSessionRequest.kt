package com.miyou.app.api.dialogue.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateSessionRequest(
    @field:NotBlank(message = "userId is required")
    @field:Size(max = 128, message = "userId too long")
    val userId: String = "",
    // PersonaId(빈값/null 허용) 검증 동기화. Jackson 역직렬화 예외 방지용 String? 타입 적용.
    @field:Size(max = 64, message = "personaId too long")
    @field:Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "personaId contains invalid characters")
    val personaId: String? = null,
)
