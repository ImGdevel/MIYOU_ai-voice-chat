package com.miyou.app.infrastructure.inbound.web.dialogue.dto

import jakarta.validation.constraints.NotBlank

data class CreateSessionRequest(
    @field:NotBlank(message = "userId is required")
    val userId: String = "",
    val personaId: String = "",
)
