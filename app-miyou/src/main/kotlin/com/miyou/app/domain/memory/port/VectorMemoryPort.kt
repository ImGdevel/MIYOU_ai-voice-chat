package com.miyou.app.domain.memory.port

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryType
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface VectorMemoryPort {
    fun upsert(
        memory: Memory,
        embedding: List<Float>,
    ): Mono<Memory>

    fun search(
        sessionId: ConversationSessionId,
        queryEmbedding: List<Float>,
        types: List<MemoryType>,
        importanceThreshold: Float,
        topK: Int,
    ): Flux<Memory>

    fun updateImportance(
        memoryId: String,
        newImportance: Float,
        lastAccessedAt: Instant,
        accessCount: Int,
    ): Mono<Void>
}
