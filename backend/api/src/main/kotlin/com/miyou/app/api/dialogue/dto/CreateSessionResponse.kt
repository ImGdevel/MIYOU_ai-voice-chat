package com.miyou.app.api.dialogue.dto

data class CreateSessionResponse(
    val sessionId: String,
    val userId: String,
    val personaId: String,
)
