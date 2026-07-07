package com.miyou.app.infrastructure.dialogue.adapter.persistence

import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationDocument
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import com.miyou.app.infrastructure.dialogue.repository.ConversationMongoRepository
import com.miyou.app.support.anyStringValue
import com.miyou.app.support.anyValue
import com.miyou.app.support.eqValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.ReactiveListOperations
import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class ConversationCachingAdapterTest {
    @Mock
    private lateinit var mongoRepository: ConversationMongoRepository

    @Mock
    private lateinit var redisTemplate: ReactiveRedisTemplate<String, String>

    @Mock
    private lateinit var listOps: ReactiveListOperations<String, String>

    private lateinit var adapter: ConversationCachingAdapter

    @BeforeEach
    fun setUp() {
        val properties = RagDialogueProperties()
        properties.cache.maxHistorySize = 10
        properties.cache.ttlHours = 24
        adapter = ConversationCachingAdapter(mongoRepository, redisTemplate, properties)
    }

    @Test
    @DisplayName("save persists to MongoDB and appends the turn to Redis")
    fun save_persistsToMongoAndAppendsToRedis() {
        val sessionId = ConversationSessionFixture.createId()
        val turn = ConversationTurn.create(sessionId, "hello")
        val cacheKey = "dialogue:conversation:history:${sessionId.value}"
        val saved = ConversationDocument("id-1", sessionId.value, "hello", null, Instant.now())

        `when`(mongoRepository.save(anyValue())).thenReturn(Mono.just(saved))
        `when`(redisTemplate.opsForList()).thenReturn(listOps)
        `when`(listOps.rightPush(eqValue(cacheKey), anyStringValue())).thenReturn(Mono.just(1L))
        `when`(listOps.trim(cacheKey, -10L, -1L)).thenReturn(Mono.just(true))
        `when`(redisTemplate.expire(cacheKey, java.time.Duration.ofHours(24))).thenReturn(Mono.just(true))

        StepVerifier
            .create(adapter.save(turn))
            .assertNext { result -> assertThat(result.id).isEqualTo("id-1") }
            .verifyComplete()
    }

    @Test
    @DisplayName("findRecent returns cached turns without hitting MongoDB")
    fun findRecent_returnsCachedTurnsWithoutMongoLookup() {
        val sessionId = ConversationSessionFixture.createId()
        val cacheKey = "dialogue:conversation:history:${sessionId.value}"
        val now = Instant.now()
        val json =
            """{"id":"id-1","sessionId":"${sessionId.value}","query":"question","response":"answer","createdAt":"$now"}"""

        `when`(redisTemplate.opsForList()).thenReturn(listOps)
        `when`(listOps.range(cacheKey, -5L, -1L)).thenReturn(Flux.just(json))

        StepVerifier
            .create(adapter.findRecent(sessionId, 5))
            .assertNext { result ->
                assertThat(result.id).isEqualTo("id-1")
                assertThat(result.query).isEqualTo("question")
            }.verifyComplete()

        verify(mongoRepository, never()).findBySessionIdOrderByCreatedAtAsc(sessionId.value, PageRequest.of(0, 5))
    }

    @Test
    @DisplayName("evict deletes the Redis history key")
    fun evict_deletesRedisHistoryKey() {
        val sessionId = ConversationSessionFixture.createId()
        val cacheKey = "dialogue:conversation:history:${sessionId.value}"

        `when`(redisTemplate.delete(cacheKey)).thenReturn(Mono.just(1L))

        StepVerifier.create(adapter.evict(sessionId)).verifyComplete()

        verify(redisTemplate).delete(cacheKey)
    }
}
