package com.miyou.app.domain.memory.port

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import reactor.core.publisher.Mono

interface MemoryRetrievalPort {
    fun retrieveMemories(
        sessionId: ConversationSessionId,
        query: String,
        topK: Int,
    ): Mono<MemoryRetrievalResult>
}
