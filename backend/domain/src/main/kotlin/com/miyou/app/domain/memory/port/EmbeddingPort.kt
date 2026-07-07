package com.miyou.app.domain.memory.port

import com.miyou.app.domain.memory.model.MemoryEmbedding
import reactor.core.publisher.Mono

interface EmbeddingPort {
    fun embed(text: String): Mono<MemoryEmbedding>
}
