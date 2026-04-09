package com.miyou.app.infrastructure.retrieval.adapter

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.domain.retrieval.port.RetrievalPort
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class InMemoryRetrievalAdapter(
    private val conversationRepository: ConversationRepository,
) : RetrievalPort {
    override fun retrieve(
        sessionId: ConversationSessionId,
        query: String,
        topK: Int,
    ): Mono<RetrievalContext> =
        conversationRepository
            .findRecent(sessionId, topK)
            .collectList()
            .map { turns -> KeywordSimilaritySupport.rankDocumentsByQuery(query, turns, topK) }
            .map { documents -> RetrievalContext.of(query, documents) }

    override fun retrieveMemories(
        sessionId: ConversationSessionId,
        query: String,
        topK: Int,
    ): Mono<MemoryRetrievalResult> = Mono.just(MemoryRetrievalResult.empty())
}
