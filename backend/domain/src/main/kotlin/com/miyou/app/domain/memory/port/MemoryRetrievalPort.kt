package com.miyou.app.domain.memory.port

import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import reactor.core.publisher.Mono

interface MemoryRetrievalPort {
    fun retrieveMemories(
        sessionId: String,
        query: String,
        topK: Int,
    ): Mono<MemoryRetrievalResult>
}
