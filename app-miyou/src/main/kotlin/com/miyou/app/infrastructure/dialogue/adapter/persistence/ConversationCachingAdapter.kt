package com.miyou.app.infrastructure.dialogue.adapter.persistence

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationDocument
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import com.miyou.app.infrastructure.dialogue.repository.ConversationMongoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

@Component
class ConversationCachingAdapter(
    private val mongoRepository: ConversationMongoRepository,
    @Qualifier("reactiveRedisStringTemplate") private val redisTemplate: ReactiveRedisTemplate<String, String>,
    properties: RagDialogueProperties,
) : ConversationRepository {
    private val log = LoggerFactory.getLogger(javaClass)
    private val maxCacheSize = properties.cache.maxHistorySize
    private val cacheTtl = Duration.ofHours(properties.cache.ttlHours.toLong())
    private val objectMapper =
        jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    override fun save(turn: ConversationTurn): Mono<ConversationTurn> {
        val document = toDocument(turn)
        return mongoRepository
            .save(document)
            .map(::toConversationTurn)
            .flatMap { saved ->
                Mono
                    .defer { appendToCache(saved) }
                    .onErrorResume { e ->
                        log.warn(
                            "Redis cache write failed for session {}, continuing without cache",
                            saved.sessionId().value(),
                            e
                        )
                        Mono.empty()
                    }.thenReturn(saved)
            }
    }

    override fun findRecent(
        sessionId: ConversationSessionId,
        limit: Int,
    ): Flux<ConversationTurn> {
        val key = historyKey(sessionId)
        return redisTemplate
            .opsForList()
            .range(key, (-limit).toLong(), -1)
            .map(::deserialize)
            .onErrorResume { e ->
                log.warn("Redis cache read failed for session {}, falling back to MongoDB", sessionId.value(), e)
                Flux.empty()
            }.switchIfEmpty(Flux.defer { loadFromMongoAndWarmup(sessionId, key, limit) })
    }

    private fun loadFromMongoAndWarmup(
        sessionId: ConversationSessionId,
        key: String,
        limit: Int,
    ): Flux<ConversationTurn> =
        mongoRepository
            .findBySessionIdOrderByCreatedAtAsc(sessionId.value(), PageRequest.of(0, limit))
            .map(::toConversationTurn)
            .collectList()
            .flatMapMany { list ->
                warmupCache(key, list)
                    .onErrorResume { e ->
                        log.warn("Redis cache warmup failed for session {}", sessionId.value(), e)
                        Mono.empty()
                    }.thenMany(Flux.fromIterable(list))
            }

    private fun appendToCache(turn: ConversationTurn): Mono<Void> {
        val key = historyKey(turn.sessionId())
        val json = serialize(turn)
        return redisTemplate
            .opsForList()
            .rightPush(key, json)
            .then(redisTemplate.opsForList().trim(key, -maxCacheSize.toLong(), -1))
            .then(redisTemplate.expire(key, cacheTtl))
            .then()
    }

    private fun warmupCache(
        key: String,
        turns: List<ConversationTurn>,
    ): Mono<Void> {
        if (turns.isEmpty()) {
            return Mono.empty()
        }
        val values =
            turns
                .map(::serialize)
                .toTypedArray()
        return redisTemplate
            .opsForList()
            .rightPushAll(key, *values)
            .then(redisTemplate.opsForList().trim(key, -maxCacheSize.toLong(), -1))
            .then(redisTemplate.expire(key, cacheTtl))
            .then()
    }

    fun evict(sessionId: ConversationSessionId): Mono<Void> = redisTemplate.delete(historyKey(sessionId)).then()

    private fun historyKey(sessionId: ConversationSessionId): String = KEY_PREFIX + sessionId.value()

    private fun serialize(turn: ConversationTurn): String {
        try {
            return objectMapper.writeValueAsString(
                ConversationTurnDto(
                    turn.id(),
                    turn.sessionId().value(),
                    turn.query(),
                    turn.response(),
                    turn.createdAt(),
                ),
            )
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("Failed to serialize ConversationTurn", e)
        }
    }

    private fun deserialize(json: String): ConversationTurn {
        try {
            val dto = objectMapper.readValue(json, ConversationTurnDto::class.java)
            return ConversationTurn.withId(
                dto.id,
                ConversationSessionId.of(dto.sessionId),
                dto.query,
                dto.response,
                dto.createdAt,
            )
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("Failed to deserialize ConversationTurn", e)
        }
    }

    private fun toDocument(turn: ConversationTurn): ConversationDocument =
        ConversationDocument(
            turn.id(),
            turn.sessionId().value(),
            turn.query(),
            turn.response(),
            turn.createdAt(),
        )

    private fun toConversationTurn(document: ConversationDocument): ConversationTurn =
        ConversationTurn.withId(
            document.id,
            ConversationSessionId.of(document.sessionId),
            document.query,
            document.response,
            document.createdAt,
        )

    private data class ConversationTurnDto(
        val id: String?,
        val sessionId: String,
        val query: String,
        val response: String?,
        val createdAt: Instant,
    )

    private companion object {
        const val KEY_PREFIX = "dialogue:conversation:history:"
    }
}
