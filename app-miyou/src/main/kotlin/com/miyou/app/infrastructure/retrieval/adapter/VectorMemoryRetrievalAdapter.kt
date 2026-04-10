package com.miyou.app.infrastructure.retrieval.adapter

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.memory.port.MemoryRetrievalPort
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.domain.retrieval.port.RetrievalPort
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
@Primary
class VectorMemoryRetrievalAdapter(
    private val memoryRetrievalPort: MemoryRetrievalPort,
    private val conversationRepository: ConversationRepository,
) : RetrievalPort {
    private val log: Logger = LoggerFactory.getLogger(VectorMemoryRetrievalAdapter::class.java)

    override fun retrieve(
        sessionId: ConversationSessionId,
        query: String,
        topK: Int,
    ): Mono<RetrievalContext> =
        conversationRepository
            .findRecent(sessionId, topK * 10)
            .collectList()
            .map { turns -> KeywordSimilaritySupport.rankDocumentsByQuery(query, turns, topK) }
            .map { documents -> RetrievalContext.of(query, documents) }

    override fun retrieveMemories(
        sessionId: ConversationSessionId,
        query: String,
        topK: Int,
    ): Mono<MemoryRetrievalResult> =
        memoryRetrievalPort
            .retrieveMemories(sessionId, query, topK)
            .onErrorResume { error ->
                log.warn("Memory retrieval failed for query '{}': {}", query, error.message, error)
                Mono.just(MemoryRetrievalResult.empty())
            }
}
