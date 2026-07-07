package com.miyou.app.api.dialogue.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateSessionRequest(
    @field:NotBlank(message = "userId is required")
    @field:Size(max = 128, message = "userId too long")
    val userId: String = "",
    val personaId: String = "",
)
