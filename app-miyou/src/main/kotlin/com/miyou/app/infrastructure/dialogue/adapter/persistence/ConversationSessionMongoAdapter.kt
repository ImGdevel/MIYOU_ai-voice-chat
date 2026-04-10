package com.miyou.app.infrastructure.dialogue.adapter.persistence

import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationSessionDocument
import com.miyou.app.infrastructure.dialogue.repository.ConversationSessionMongoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class ConversationSessionMongoAdapter(
    private val mongoRepository: ConversationSessionMongoRepository,
    @Qualifier("reactiveRedisStringTemplate") private val redisTemplate: ReactiveRedisTemplate<String, String>,
) : ConversationSessionRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun save(session: ConversationSession): Mono<ConversationSession> {
        val document = toDocument(session)
        return mongoRepository.save(document).map(::toDomain)
    }

    override fun findById(sessionId: ConversationSessionId): Mono<ConversationSession> =
        mongoRepository.findById(sessionId.value()).map(::toDomain)

    override fun findByUserId(userId: UserId): Flux<ConversationSession> =
        mongoRepository.findActiveByUserId(userId.value(), CREATED_AT_DESC).map(::toDomain)

    override fun findByPersonaId(personaId: PersonaId): Flux<ConversationSession> =
        mongoRepository.findActiveByPersonaId(personaId.value(), CREATED_AT_DESC).map(::toDomain)

    override fun findByPersonaIdAndUserId(
        personaId: PersonaId,
        userId: UserId,
    ): Flux<ConversationSession> =
        mongoRepository
            .findActiveByPersonaIdAndUserId(
                personaId.value(),
                userId.value(),
                CREATED_AT_DESC,
            ).map(::toDomain)

    override fun softDelete(sessionId: ConversationSessionId): Mono<ConversationSession> =
        mongoRepository
            .findById(sessionId.value())
            .flatMap { document ->
                val deleted = toDomain(document).softDelete()
                mongoRepository
                    .save(toDocument(deleted))
                    .map(::toDomain)
            }.flatMap { deleted ->
                evictHistoryCache(sessionId)
                    .onErrorResume { e ->
                        log.warn(
                            "Failed to evict history cache for session {} on soft-delete",
                            sessionId.value(),
                            e,
                        )
                        Mono.empty()
                    }.thenReturn(deleted)
            }

    private fun evictHistoryCache(sessionId: ConversationSessionId): Mono<Void> =
        redisTemplate.delete(HISTORY_KEY_PREFIX + sessionId.value()).then()

    private fun toDocument(session: ConversationSession): ConversationSessionDocument =
        ConversationSessionDocument(
            session.sessionId().value(),
            session.personaId().value(),
            session.userId().value(),
            session.createdAt(),
            session.deletedAt(),
        )

    private fun toDomain(document: ConversationSessionDocument): ConversationSession =
        ConversationSession(
            ConversationSessionId.of(document.sessionId),
            PersonaId.of(document.personaId),
            UserId.of(document.userId),
            document.createdAt,
            document.deletedAt,
        )

    private companion object {
        const val HISTORY_KEY_PREFIX = "dialogue:conversation:history:"
        val CREATED_AT_DESC = Sort.by(Sort.Direction.DESC, "createdAt")
    }
}
