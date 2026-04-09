package com.miyou.app.infrastructure.dialogue.adapter.persistence

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationDocument
import com.miyou.app.infrastructure.dialogue.repository.ConversationMongoRepository
import org.springframework.data.domain.PageRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * MongoDB 기반 ConversationRepository 보조 구현체.
 * 주 캐시 흐름은 ConversationCachingAdapter가 담당.
 */
class ConversationMongoAdapter(
    private val mongoRepository: ConversationMongoRepository,
) : ConversationRepository {
    override fun save(turn: ConversationTurn): Mono<ConversationTurn> {
        val document =
            ConversationDocument(
                turn.id(),
                turn.sessionId().value(),
                turn.query(),
                turn.response(),
                turn.createdAt(),
            )
        return mongoRepository.save(document).map(::toConversationTurn)
    }

    override fun findRecent(
        sessionId: ConversationSessionId,
        limit: Int,
    ): Flux<ConversationTurn> =
        mongoRepository
            .findBySessionIdOrderByCreatedAtDesc(sessionId.value(), PageRequest.of(0, limit))
            .map(::toConversationTurn)
            .collectList()
            .flatMapMany { documents ->
                documents.reversed().let { list -> Flux.fromIterable(list) }
            }

    private fun toConversationTurn(document: ConversationDocument): ConversationTurn =
        ConversationTurn.withId(
            document.id,
            ConversationSessionId.of(document.sessionId),
            document.query,
            document.response,
            document.createdAt,
        )
}
