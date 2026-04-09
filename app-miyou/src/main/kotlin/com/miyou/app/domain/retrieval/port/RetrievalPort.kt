package com.miyou.app.domain.retrieval.port

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import reactor.core.publisher.Mono

interface RetrievalPort {
    fun retrieve(
        sessionId: ConversationSessionId,
        query: String,
        topK: Int,
    ): Mono<RetrievalContext>

    fun retrieveMemories(
        sessionId: ConversationSessionId,
        query: String,
        topK: Int,
    ): Mono<MemoryRetrievalResult>
}
