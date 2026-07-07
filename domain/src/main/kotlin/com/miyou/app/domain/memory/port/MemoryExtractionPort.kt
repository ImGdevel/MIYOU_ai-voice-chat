package com.miyou.app.domain.memory.port

import com.miyou.app.domain.memory.model.ExtractedMemory
import com.miyou.app.domain.memory.model.MemoryExtractionContext
import reactor.core.publisher.Flux

interface MemoryExtractionPort {
    fun extractMemories(context: MemoryExtractionContext): Flux<ExtractedMemory>
}
