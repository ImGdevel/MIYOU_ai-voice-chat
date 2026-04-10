package com.miyou.app.domain.memory.port

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import reactor.core.publisher.Mono

interface ConversationCounterPort {
    fun increment(sessionId: ConversationSessionId): Mono<Long>

    fun get(sessionId: ConversationSessionId): Mono<Long>

    fun reset(sessionId: ConversationSessionId): Mono<Void>
}
