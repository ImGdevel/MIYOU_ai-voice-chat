package com.study.webflux.rag.infrastructure.memory.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.memory.port.ConversationCounterPort;
import reactor.core.publisher.Mono;

/** Redis에서 대화 카운터를 관리하는 어댑터입니다. */
@Component
@RequiredArgsConstructor
public class RedisConversationCounterAdapter implements ConversationCounterPort {

	private static final String COUNTER_KEY = "dialogue:conversation:counter";

	private final ReactiveRedisTemplate<String, Long> redisTemplate;

	/** 대화 수를 Redis에서 원자적으로 증가시킵니다. */
	@Override
	public Mono<Long> increment() {
		return redisTemplate.opsForValue().increment(COUNTER_KEY);
	}

	/** 현재 카운터 값을 조회합니다. */
	@Override
	public Mono<Long> get() {
		return redisTemplate.opsForValue().get(COUNTER_KEY).defaultIfEmpty(0L);
	}

	/** Redis 키를 삭제하여 카운터를 초기화합니다. */
	@Override
	public Mono<Void> reset() {
		return redisTemplate.delete(COUNTER_KEY).then();
	}
}
