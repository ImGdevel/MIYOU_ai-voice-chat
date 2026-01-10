package com.study.webflux.rag.infrastructure.memory.adapter;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisConversationCounterAdapterTest {

	private static final String COUNTER_KEY_PREFIX = "dialogue:conversation:counter:";

	@Mock
	private ReactiveRedisTemplate<String, Long> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, Long> valueOps;

	private RedisConversationCounterAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new RedisConversationCounterAdapter(redisTemplate);
	}

	@Test
	@DisplayName("카운터 증가 시 Redis INCR 명령 실행")
	void increment_success() {
		UserId userId = UserId.of("user-1");
		String counterKey = COUNTER_KEY_PREFIX + userId.value();
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(valueOps.increment(counterKey)).thenReturn(Mono.just(1L));

		StepVerifier.create(adapter.increment(userId)).expectNext(1L).verifyComplete();

		verify(valueOps).increment(counterKey);
	}

	@Test
	@DisplayName("여러 번 증가 시 순차적으로 증가")
	void increment_multiple() {
		UserId userId = UserId.of("user-1");
		String counterKey = COUNTER_KEY_PREFIX + userId.value();
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(valueOps.increment(counterKey)).thenReturn(Mono.just(1L)).thenReturn(Mono.just(2L))
			.thenReturn(Mono.just(3L));

		StepVerifier.create(adapter.increment(userId)).expectNext(1L).verifyComplete();
		StepVerifier.create(adapter.increment(userId)).expectNext(2L).verifyComplete();
		StepVerifier.create(adapter.increment(userId)).expectNext(3L).verifyComplete();
	}

	@Test
	@DisplayName("카운터 조회 시 현재 값 반환")
	void get_success() {
		UserId userId = UserId.of("user-1");
		String counterKey = COUNTER_KEY_PREFIX + userId.value();
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(valueOps.get(counterKey)).thenReturn(Mono.just(42L));

		StepVerifier.create(adapter.get(userId)).expectNext(42L).verifyComplete();

		verify(valueOps).get(counterKey);
	}

	@Test
	@DisplayName("카운터가 없으면 0 반환")
	void get_notExists_returnsZero() {
		UserId userId = UserId.of("user-1");
		String counterKey = COUNTER_KEY_PREFIX + userId.value();
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(valueOps.get(counterKey)).thenReturn(Mono.empty());

		StepVerifier.create(adapter.get(userId)).expectNext(0L).verifyComplete();
	}

	@Test
	@DisplayName("카운터 리셋 시 키 삭제")
	void reset_success() {
		UserId userId = UserId.of("user-1");
		String counterKey = COUNTER_KEY_PREFIX + userId.value();
		when(redisTemplate.delete(counterKey)).thenReturn(Mono.just(1L));

		StepVerifier.create(adapter.reset(userId)).verifyComplete();

		verify(redisTemplate).delete(counterKey);
	}

	@Test
	@DisplayName("카운터 리셋 후 조회 시 0 반환")
	void reset_thenGet_returnsZero() {
		UserId userId = UserId.of("user-1");
		String counterKey = COUNTER_KEY_PREFIX + userId.value();
		when(redisTemplate.delete(counterKey)).thenReturn(Mono.just(1L));
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(valueOps.get(counterKey)).thenReturn(Mono.empty());

		StepVerifier.create(adapter.reset(userId)).verifyComplete();
		StepVerifier.create(adapter.get(userId)).expectNext(0L).verifyComplete();
	}

	@Test
	@DisplayName("증가 실패 시 에러 전파")
	void increment_error_propagates() {
		UserId userId = UserId.of("user-1");
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(valueOps.increment(anyString())).thenReturn(
			Mono.error(new RuntimeException("Redis connection error")));

		StepVerifier.create(adapter.increment(userId))
			.expectErrorMatches(
				throwable -> throwable.getMessage().contains("Redis connection error"))
			.verify();
	}

	@Test
	@DisplayName("조회 실패 시 에러 전파")
	void get_error_propagates() {
		UserId userId = UserId.of("user-1");
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(valueOps.get(anyString())).thenReturn(
			Mono.error(new RuntimeException("Redis timeout")));

		StepVerifier.create(adapter.get(userId))
			.expectErrorMatches(throwable -> throwable.getMessage().contains("Redis timeout"))
			.verify();
	}
}
