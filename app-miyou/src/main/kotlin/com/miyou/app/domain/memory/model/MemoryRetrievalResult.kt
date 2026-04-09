package com.miyou.app.domain.memory.model

data class MemoryRetrievalResult(
    val experientialMemories: List<Memory> = emptyList(),
    val factualMemories: List<Memory> = emptyList(),
) {
    val allMemories: List<Memory> by lazy { experientialMemories + factualMemories }

    fun isEmpty(): Boolean = experientialMemories.isEmpty() && factualMemories.isEmpty()

    fun totalCount(): Int = experientialMemories.size + factualMemories.size

    fun allMemories(): List<Memory> = allMemories

    companion object {
        @JvmStatic
        fun empty(): MemoryRetrievalResult = MemoryRetrievalResult(emptyList(), emptyList())

        @JvmStatic
        fun of(
            experiential: List<Memory>,
            factual: List<Memory>,
        ): MemoryRetrievalResult = MemoryRetrievalResult(experiential, factual)
    }
}
