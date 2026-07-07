package com.miyou.app.domain.retrieval.model

data class SimilarityScore(
    val value: Int,
) {
    init {
        require(value >= 0) { "Similarity score cannot be negative" }
    }

    fun isRelevant(): Boolean = value > 0

    fun value(): Int = value

    companion object {
        @JvmStatic
        fun of(value: Int): SimilarityScore = SimilarityScore(value)
    }
}
