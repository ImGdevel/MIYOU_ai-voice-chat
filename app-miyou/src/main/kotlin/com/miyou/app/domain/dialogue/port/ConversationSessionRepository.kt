package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.model.UserId
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ConversationSessionRepository {
    fun save(session: ConversationSession): Mono<ConversationSession>

    fun findById(sessionId: ConversationSessionId): Mono<ConversationSession>

    fun findByUserId(userId: UserId): Flux<ConversationSession>

    fun findByPersonaId(personaId: PersonaId): Flux<ConversationSession>

    fun findByPersonaIdAndUserId(
        personaId: PersonaId,
        userId: UserId,
    ): Flux<ConversationSession>

    fun softDelete(sessionId: ConversationSessionId): Mono<ConversationSession>
}
