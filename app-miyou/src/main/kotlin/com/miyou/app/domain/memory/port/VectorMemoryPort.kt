package com.miyou.app.domain.memory.port

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
        sessionId: String,
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

    /** 아직 아카이브되지 않은(archivedAt이 없는) 메모리를 batchSize 단위로 페이지네이션하며 스트리밍한다. */
    fun findAllActive(batchSize: Int): Flux<Memory>

    /** 큐레이터 배치가 계산한 감쇠된 importance / (필요 시) archivedAt을 저장소에 반영한다. */
    fun applyDecayAndArchive(memory: Memory): Mono<Void>
}
