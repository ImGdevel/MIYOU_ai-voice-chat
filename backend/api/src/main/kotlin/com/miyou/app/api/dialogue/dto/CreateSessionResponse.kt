package com.miyou.app.api.dialogue.dto

import com.miyou.app.domain.dialogue.model.ConversationSession
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "세션 생성 응답 DTO")
data class CreateSessionResponse(
    @field:Schema(description = "생성된 세션 고유 ID", example = "session_20260708_98765abcd")
    val sessionId: String,
    @field:Schema(description = "사용자 ID", example = "user-123")
    val userId: String,
    @field:Schema(description = "사용자와 대화하는 상대방 퍼소나 ID", example = "persona_gentle_bob")
    val personaId: String,
) {
    companion object {
        fun from(session: ConversationSession): CreateSessionResponse =
            CreateSessionResponse(session.sessionId.value, session.userId, session.personaId.value)
    }
}
