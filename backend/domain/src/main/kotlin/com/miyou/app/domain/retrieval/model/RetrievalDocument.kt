package com.miyou.app.domain.retrieval.model

data class RetrievalDocument(
    val content: String,
    val score: SimilarityScore,
    val metadata: Map<String, Any> = emptyMap(),
) {
    init {
        require(content.isNotBlank()) { "content cannot be null or blank" }
    }

    companion object {
        @JvmStatic
        fun of(
            content: String,
            score: Int,
        ): RetrievalDocument = RetrievalDocument(content, SimilarityScore.of(score), emptyMap())

        @JvmStatic
        fun withMetadata(
            content: String,
            score: Int,
            metadata: Map<String, Any>?,
        ): RetrievalDocument = RetrievalDocument(content, SimilarityScore.of(score), metadata.orEmpty())
    }

    fun content(): String = content

    fun score(): SimilarityScore = score

    fun metadata(): Map<String, Any> = metadata
}
