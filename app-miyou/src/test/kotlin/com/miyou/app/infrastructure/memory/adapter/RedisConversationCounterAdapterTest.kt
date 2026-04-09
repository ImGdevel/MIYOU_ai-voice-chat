package com.miyou.app.infrastructure.memory.adapter

import com.miyou.app.fixture.ConversationSessionFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
class RedisConversationCounterAdapterTest {

	private val counterKeyPrefix = "dialogue:conversation:counter:"

	@Mock
	private lateinit var redisTemplate: ReactiveRedisTemplate<String, Long>

	@Mock
	private lateinit var valueOps: ReactiveValueOperations<String, Long>

	private lateinit var adapter: RedisConversationCounterAdapter

	@BeforeEach
	fun setUp() {
		adapter = RedisConversationCounterAdapter(redisTemplate)
	}

	@Test
	@DisplayName("카운터 증가 시 Redis INCR 명령을 실행한다")
	fun increment_success() {
		val sessionId = ConversationSessionFixture.createId()
		val counterKey = "$counterKeyPrefix${sessionId.value()}"
		`when`(redisTemplate.opsForValue()).thenReturn(valueOps)
		`when`(valueOps.increment(counterKey)).thenReturn(Mono.just(1L))

		StepVerifier.create(adapter.increment(sessionId))
			.expectNext(1L)
			.verifyComplete()

		verify(valueOps).increment(counterKey)
	}

	@Test
	@DisplayName("여러 번 증가 시 순차적으로 증가한다")
	fun increment_multiple() {
		val sessionId = ConversationSessionFixture.createId()
		val counterKey = "$counterKeyPrefix${sessionId.value()}"
		`when`(redisTemplate.opsForValue()).thenReturn(valueOps)
		`when`(valueOps.increment(counterKey))
			.thenReturn(Mono.just(1L))
			.thenReturn(Mono.just(2L))
			.thenReturn(Mono.just(3L))

		StepVerifier.create(adapter.increment(sessionId)).expectNext(1L).verifyComplete()
		StepVerifier.create(adapter.increment(sessionId)).expectNext(2L).verifyComplete()
		StepVerifier.create(adapter.increment(sessionId)).expectNext(3L).verifyComplete()
	}

	@Test
	@DisplayName("카운터 조회 시 현재 값을 반환한다")
	fun get_success() {
		val sessionId = ConversationSessionFixture.createId()
		val counterKey = "$counterKeyPrefix${sessionId.value()}"
		`when`(redisTemplate.opsForValue()).thenReturn(valueOps)
		`when`(valueOps.get(counterKey)).thenReturn(Mono.just(42L))

		StepVerifier.create(adapter.get(sessionId)).expectNext(42L).verifyComplete()

		verify(valueOps).get(counterKey)
	}

	@Test
	@DisplayName("카운터가 없으면 0을 반환한다")
	fun get_notExists_returnsZero() {
		val sessionId = ConversationSessionFixture.createId()
		val counterKey = "$counterKeyPrefix${sessionId.value()}"
		`when`(redisTemplate.opsForValue()).thenReturn(valueOps)
		`when`(valueOps.get(counterKey)).thenReturn(Mono.empty())

		StepVerifier.create(adapter.get(sessionId)).expectNext(0L).verifyComplete()
	}

	@Test
	@DisplayName("카운터 리셋 시 키를 삭제한다")
	fun reset_success() {
		val sessionId = ConversationSessionFixture.createId()
		val counterKey = "$counterKeyPrefix${sessionId.value()}"
		`when`(redisTemplate.delete(counterKey)).thenReturn(Mono.just(1L))

		StepVerifier.create(adapter.reset(sessionId)).verifyComplete()

		verify(redisTemplate).delete(counterKey)
	}

	@Test
	@DisplayName("카운터 리셋 후 조회 시 0을 반환한다")
	fun reset_thenGet_returnsZero() {
		val sessionId = ConversationSessionFixture.createId()
		val counterKey = "$counterKeyPrefix${sessionId.value()}"
		`when`(redisTemplate.delete(counterKey)).thenReturn(Mono.just(1L))
		`when`(redisTemplate.opsForValue()).thenReturn(valueOps)
		`when`(valueOps.get(counterKey)).thenReturn(Mono.empty())

		StepVerifier.create(adapter.reset(sessionId)).verifyComplete()
		StepVerifier.create(adapter.get(sessionId)).expectNext(0L).verifyComplete()
	}

	@Test
	@DisplayName("증가 실패 시 에러를 전파한다")
	fun increment_error_propagates() {
		val sessionId = ConversationSessionFixture.createId()
		`when`(redisTemplate.opsForValue()).thenReturn(valueOps)
		`when`(valueOps.increment(anyString())).thenReturn(Mono.error(RuntimeException("Redis connection error")))

		StepVerifier.create(adapter.increment(sessionId))
			.expectErrorMatches { throwable -> throwable.message!!.contains("Redis connection error") }
			.verify()
	}

	@Test
	@DisplayName("조회 실패 시 에러를 전파한다")
	fun get_error_propagates() {
		val sessionId = ConversationSessionFixture.createId()
		`when`(redisTemplate.opsForValue()).thenReturn(valueOps)
		`when`(valueOps.get(anyString())).thenReturn(Mono.error(RuntimeException("Redis timeout")))

		StepVerifier.create(adapter.get(sessionId))
			.expectErrorMatches { throwable -> throwable.message!!.contains("Redis timeout") }
			.verify()
	}
}
