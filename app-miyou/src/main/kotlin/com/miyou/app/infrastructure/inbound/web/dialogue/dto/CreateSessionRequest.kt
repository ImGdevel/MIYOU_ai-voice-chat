package com.miyou.app.infrastructure.inbound.web.dialogue.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateSessionRequest(
    @field:NotBlank(message = "userId is required")
    @field:Size(max = 128, message = "userId must be at most 128 characters")
    val userId: String = "",
    val personaId: String = "",
)
