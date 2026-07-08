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
        val objectMapper =
            com.fasterxml.jackson.module.kotlin
                .jacksonObjectMapper()
                .registerModule(
                    com.fasterxml.jackson.datatype.jsr310
                        .JavaTimeModule()
                ).disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        adapter = ConversationCachingAdapter(mongoRepository, redisTemplate, objectMapper, properties)
    }

    @Test
    @DisplayName("save는 MongoDB에 영속화하고 Redis에 대화 턴을 추가한다")
    fun save_persistsToMongoAndAppendsToRedis() {
        val sessionId = ConversationSessionFixture.createId()
        val turn = ConversationTurn.create(sessionId, "hello")
        val cacheKey = "dialogue:conversation:history:${sessionId.value}"
        val saved = ConversationDocument("id-1", sessionId.value, "hello", null, Instant.now())

        // 모의 객체 설정: MongoDB 저장 성공 처리 및 Redis 목록 푸시/트림/TTL 만료 설정
        `when`(mongoRepository.save(anyValue())).thenReturn(Mono.just(saved))
        `when`(redisTemplate.opsForList()).thenReturn(listOps)
        `when`(listOps.rightPush(eqValue(cacheKey), anyStringValue())).thenReturn(Mono.just(1L))
        `when`(listOps.trim(cacheKey, -10L, -1L)).thenReturn(Mono.just(true))
        `when`(redisTemplate.expire(cacheKey, java.time.Duration.ofHours(24))).thenReturn(Mono.just(true))

        // 실행 및 검증: MongoDB 저장 성공 후 반환되는 도큐먼트 ID 검증
        StepVerifier
            .create(adapter.save(turn))
            .assertNext { result -> assertThat(result.id).isEqualTo("id-1") }
            .verifyComplete()
    }

    @Test
    @DisplayName("findRecent는 MongoDB를 조회하지 않고 캐싱된 대화 턴을 반환한다")
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
    @DisplayName("evict는 Redis의 대화 이력 키를 삭제한다")
    fun evict_deletesRedisHistoryKey() {
        val sessionId = ConversationSessionFixture.createId()
        val cacheKey = "dialogue:conversation:history:${sessionId.value}"

        `when`(redisTemplate.delete(cacheKey)).thenReturn(Mono.just(1L))

        StepVerifier.create(adapter.evict(sessionId)).verifyComplete()

        verify(redisTemplate).delete(cacheKey)
    }
}
