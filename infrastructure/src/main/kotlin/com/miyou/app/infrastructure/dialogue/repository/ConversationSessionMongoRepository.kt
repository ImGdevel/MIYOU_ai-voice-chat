package com.miyou.app.infrastructure.dialogue.repository

import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationSessionDocument
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux

interface ConversationSessionMongoRepository : ReactiveMongoRepository<ConversationSessionDocument, String> {
    @Query("{'userId': ?0, 'deletedAt': null}")
    fun findActiveByUserId(
        userId: String,
        sort: Sort,
    ): Flux<ConversationSessionDocument>

    @Query("{'personaId': ?0, 'deletedAt': null}")
    fun findActiveByPersonaId(
        personaId: String,
        sort: Sort,
    ): Flux<ConversationSessionDocument>

    @Query("{'personaId': ?0, 'userId': ?1, 'deletedAt': null}")
    fun findActiveByPersonaIdAndUserId(
        personaId: String,
        userId: String,
        sort: Sort,
    ): Flux<ConversationSessionDocument>
}
