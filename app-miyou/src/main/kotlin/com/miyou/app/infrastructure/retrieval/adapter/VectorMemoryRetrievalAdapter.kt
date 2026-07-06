package com.miyou.app.infrastructure.retrieval.adapter

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.domain.retrieval.port.RetrievalPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
@Primary
class VectorMemoryRetrievalAdapter(
    private val conversationRepository: ConversationRepository,
) : RetrievalPort {
    private val log = KotlinLogging.logger {}

    override fun retrieve(
        sessionId: String,
        query: String,
        topK: Int,
    ): Mono<RetrievalContext> =
        conversationRepository
            .findRecent(ConversationSessionId.of(sessionId), topK * 10)
            .collectList()
            .map { turns -> KeywordSimilaritySupport.rankDocumentsByQuery(query, turns, topK) }
            .map { documents -> RetrievalContext.of(query, documents) }
}
