package com.miyou.app.infrastructure.retrieval.adapter

import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.retrieval.model.RetrievalDocument
import java.util.Locale

internal object KeywordSimilaritySupport {
    fun rankDocumentsByQuery(
        query: String,
        turns: List<ConversationTurn>,
        topK: Int,
    ): List<RetrievalDocument> {
        val sorted =
            turns
                .map { turn ->
                    RetrievalDocument.of(
                        turn.query(),
                        scoreByTokenIntersection(query, turn.query()),
                    )
                }.filter { it.score.isRelevant() }
                .sortedByDescending { it.score.value }
                .toList()

        return if (sorted.size > topK) sorted.take(topK) else sorted
    }

    fun scoreByTokenIntersection(
        query: String,
        candidate: String,
    ): Int {
        val queryWords = tokenize(query)
        val candidateWords = tokenize(candidate)
        val intersection: MutableSet<String> = HashSet(queryWords)
        intersection.retainAll(candidateWords)
        return intersection.size
    }

    private fun tokenize(text: String?): Set<String> {
        if (text.isNullOrBlank()) {
            return emptySet()
        }
        return text
            .lowercase(Locale.ROOT)
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
