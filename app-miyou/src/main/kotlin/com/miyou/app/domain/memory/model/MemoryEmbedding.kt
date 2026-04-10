package com.miyou.app.domain.memory.model

data class MemoryEmbedding(
    val text: String,
    val vector: List<Float>,
) {
    init {
        require(text.isNotBlank()) { "text cannot be null or blank" }
        require(vector.isNotEmpty()) { "vector cannot be null or empty" }
    }

    companion object {
        @JvmStatic
        fun of(
            text: String,
            vector: List<Float>,
        ): MemoryEmbedding = MemoryEmbedding(text, vector.toList())
    }
}
