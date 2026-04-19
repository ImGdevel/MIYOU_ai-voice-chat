package com.miyou.app.infrastructure.inbound.web.dialogue.dto

data class CreateSessionRequest(
    val userId: String = "",
    val personaId: String = "",
)
