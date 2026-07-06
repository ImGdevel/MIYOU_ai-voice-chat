package com.miyou.app.domain.retrieval.port

import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import reactor.core.publisher.Mono

interface RetrievalPort {
    fun retrieve(
        sessionId: String,
        query: String,
        topK: Int,
    ): Mono<RetrievalContext>

    fun retrieveMemories(
        sessionId: String,
        query: String,
        topK: Int,
    ): Mono<MemoryRetrievalResult>
}
