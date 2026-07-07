package com.miyou.app.domain.retrieval.model

data class RetrievalContext(
    val query: String,
    val documents: List<RetrievalDocument> = emptyList(),
) {
    init {
        require(query.isNotBlank()) { "query cannot be null or blank" }
    }

    fun isEmpty(): Boolean = documents.isEmpty()

    fun documentCount(): Int = documents.size

    companion object {
        @JvmStatic
        fun empty(query: String): RetrievalContext = RetrievalContext(query, emptyList())

        @JvmStatic
        fun of(
            query: String,
            documents: List<RetrievalDocument>,
        ): RetrievalContext = RetrievalContext(query, documents)
    }

    fun documents(): List<RetrievalDocument> = documents
}
