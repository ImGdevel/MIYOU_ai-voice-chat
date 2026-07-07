package com.miyou.app.domain.dialogue.model

import java.time.Instant

data class ConversationSession(
    val sessionId: ConversationSessionId,
    val personaId: PersonaId = PersonaId.defaultPersona(),
    val userId: String,
    val createdAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
) {
    init {
        require(personaId.value.isNotBlank()) { "personaId cannot be blank" }
        require(userId.isNotBlank()) { "userId cannot be blank" }
        require(userId.length <= 128) { "userId cannot be longer than 128 characters" }
    }

    fun isActive(): Boolean = deletedAt == null

    fun softDelete(): ConversationSession = copy(deletedAt = Instant.now())

    companion object {
        @JvmStatic
        fun create(
            personaId: PersonaId,
            userId: String,
        ): ConversationSession =
            ConversationSession(
                sessionId = ConversationSessionId.generate(),
                personaId = personaId,
                userId = userId,
            )
    }
}
