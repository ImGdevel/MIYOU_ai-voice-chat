package com.miyou.app.infrastructure.inbound.web.dialogue.dto

data class CreateSessionResponse(
    val sessionId: String,
    val userId: String,
    val personaId: String,
)
