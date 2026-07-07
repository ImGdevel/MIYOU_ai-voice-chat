package com.miyou.app.infrastructure.dialogue.repository

import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationDocument
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux

interface ConversationMongoRepository : ReactiveMongoRepository<ConversationDocument, String> {
    fun findBySessionIdOrderByCreatedAtDesc(
        sessionId: String,
        pageable: Pageable,
    ): Flux<ConversationDocument>

    fun findBySessionIdOrderByCreatedAtAsc(
        sessionId: String,
        pageable: Pageable,
    ): Flux<ConversationDocument>
}
