package com.miyou.app.infrastructure.dialogue.adapter.persistence

import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationDocument
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import com.miyou.app.infrastructure.dialogue.repository.ConversationMongoRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
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

	private val keyPrefix = "dialogue:conversation:history:"

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
		val cache = RagDialogueProperties.Cache()
		cache.maxHistorySize = 10
		cache.ttlHours = 24
		properties.cache = cache
		adapter = ConversationCachingAdapter(mongoRepository, redisTemplate, properties)
	}

	@Test
	@DisplayName("저장 시 MongoDB와 Redis 캐시에 함께 반영한다")
	fun save_appendsToCache() {
		val sessionId = ConversationSessionFixture.createId()
		val turn = ConversationTurn.create(sessionId, "안녕")
		val cacheKey = "$keyPrefix${sessionId.value()}"
		val saved = ConversationDocument("id-1", sessionId.value(), "안녕", null, Instant.now())

		`when`(mongoRepository.save(any())).thenReturn(Mono.just(saved))
		`when`(redisTemplate.opsForList()).thenReturn(listOps)
		`when`(listOps.rightPush(eq(cacheKey), anyString())).thenReturn(Mono.just(1L))
		`when`(listOps.trim(eq(cacheKey), anyLong(), anyLong())).thenReturn(Mono.just(true))
		`when`(redisTemplate.expire(eq(cacheKey), any())).thenReturn(Mono.just(true))

		StepVerifier.create(adapter.save(turn))
			.assertNext { result -> assertThat(result.id()).isEqualTo("id-1") }
			.verifyComplete()
	}

	@Test
	@DisplayName("Redis 캐시 hit 시 Mongo 조회 없이 반환한다")
	fun findRecent_cacheHit_returnsCached() {
		val sessionId = ConversationSessionFixture.createId()
		val cacheKey = "$keyPrefix${sessionId.value()}"
		val now = Instant.now()
		val json =
			"""{"id":"id-1","sessionId":"${sessionId.value()}","query":"질문","response":"응답","createdAt":"$now"}"""

		`when`(redisTemplate.opsForList()).thenReturn(listOps)
		`when`(listOps.range(eq(cacheKey), anyLong(), anyLong())).thenReturn(Flux.just(json))

		StepVerifier.create(adapter.findRecent(sessionId, 5))
			.assertNext { result ->
				assertThat(result.id()).isEqualTo("id-1")
				assertThat(result.query()).isEqualTo("질문")
			}
			.verifyComplete()

		verify(mongoRepository, never()).findBySessionIdOrderByCreatedAtAsc(anyString(), any())
	}

	@Test
	@DisplayName("evict는 세션 캐시 키를 삭제한다")
	fun evict_deletesKey() {
		val sessionId = ConversationSessionFixture.createId()
		val cacheKey = "$keyPrefix${sessionId.value()}"
		`when`(redisTemplate.delete(cacheKey)).thenReturn(Mono.just(1L))

		StepVerifier.create(adapter.evict(sessionId)).verifyComplete()

		verify(redisTemplate).delete(cacheKey)
	}
}
