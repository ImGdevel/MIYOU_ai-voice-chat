package com.miyou.app.domain.dialogue.exception

class SessionNotFoundException(
    val sessionId: String,
) : RuntimeException("Session not found: $sessionId")
