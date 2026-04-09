package com.miyou.app.domain.dialogue.model

import java.time.Instant

data class ConversationSession(
    val sessionId: ConversationSessionId,
    val personaId: PersonaId = PersonaId.defaultPersona(),
    val userId: UserId,
    val createdAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
) {
    init {
        require(personaId.value.isNotBlank()) { "personaId cannot be blank" }
        require(userId.value.isNotBlank()) { "userId cannot be blank" }
    }

    fun isActive(): Boolean = deletedAt == null

    fun softDelete(): ConversationSession = copy(deletedAt = Instant.now())

    companion object {
        @JvmStatic
        fun create(
            personaId: PersonaId,
            userId: UserId,
        ): ConversationSession =
            ConversationSession(
                sessionId = ConversationSessionId.generate(),
                personaId = personaId,
                userId = userId,
            )
    }

    fun sessionId(): ConversationSessionId = sessionId

    fun personaId(): PersonaId = personaId

    fun userId(): UserId = userId

    fun createdAt(): java.time.Instant = createdAt

    fun deletedAt(): java.time.Instant? = deletedAt
}
