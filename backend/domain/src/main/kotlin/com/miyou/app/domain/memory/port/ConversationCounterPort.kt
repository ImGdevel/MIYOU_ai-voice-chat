package com.miyou.app.domain.memory.port

import reactor.core.publisher.Mono

interface ConversationCounterPort {
    fun increment(sessionId: String): Mono<Long>

    fun get(sessionId: String): Mono<Long>

    fun reset(sessionId: String): Mono<Void>
}
