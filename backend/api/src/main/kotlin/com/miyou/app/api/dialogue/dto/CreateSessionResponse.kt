package com.miyou.app.api.dialogue.dto

import com.miyou.app.domain.dialogue.model.ConversationSession

data class CreateSessionResponse(
    val sessionId: String,
    val userId: String,
    val personaId: String,
) {
    companion object {
        fun from(session: ConversationSession): CreateSessionResponse =
            CreateSessionResponse(session.sessionId.value, session.userId, session.personaId.value)
    }
}
