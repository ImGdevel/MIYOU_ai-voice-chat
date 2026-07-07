package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.ConversationTurn
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ConversationRepository {
    fun save(turn: ConversationTurn): Mono<ConversationTurn>

    fun findRecent(
        sessionId: ConversationSessionId,
        limit: Int,
    ): Flux<ConversationTurn>
}
