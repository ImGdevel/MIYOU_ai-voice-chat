package com.miyou.app.fixture

import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.PersonaId
import java.time.Instant

object ConversationSessionFixture {
    const val DEFAULT_SESSION_ID = "session-1"

    @JvmStatic
    fun create(): ConversationSession = ConversationSession.create(PersonaId.defaultPersona(), UserIdFixture.create())

    @JvmStatic
    fun create(sessionId: String): ConversationSession =
        ConversationSession(
            ConversationSessionId.of(sessionId),
            PersonaId.defaultPersona(),
            UserIdFixture.create(),
            Instant.now(),
            null,
        )

    @JvmStatic
    fun createId(): ConversationSessionId = ConversationSessionId.of(DEFAULT_SESSION_ID)

    @JvmStatic
    fun createId(sessionId: String): ConversationSessionId = ConversationSessionId.of(sessionId)
}
