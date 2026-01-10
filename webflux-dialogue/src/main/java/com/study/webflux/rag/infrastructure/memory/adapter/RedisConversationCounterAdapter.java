package com.study.webflux.rag.infrastructure.memory.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.memory.port.ConversationCounterPort;
import reactor.core.publisher.Mono;

/** Redis에서 대화 카운터를 관리하는 어댑터입니다. */
@Component
@RequiredArgsConstructor
public class RedisConversationCounterAdapter implements ConversationCounterPort {

	private static final String COUNTER_KEY_PREFIX = "dialogue:conversation:counter:";

	private final ReactiveRedisTemplate<String, Long> redisTemplate;

	private String keyFor(UserId userId) {
		return COUNTER_KEY_PREFIX + userId.value();
	}

	@Override
	public Mono<Long> increment(UserId userId) {
		return redisTemplate.opsForValue().increment(keyFor(userId));
	}

	/** 현재 카운터 값을 조회합니다. */
	@Override
	public Mono<Long> get(UserId userId) {
		return redisTemplate.opsForValue().get(keyFor(userId)).defaultIfEmpty(0L);
	}

	/** Redis 키를 삭제하여 카운터를 초기화합니다. */
	@Override
	public Mono<Void> reset(UserId userId) {
		return redisTemplate.delete(keyFor(userId)).then();
	}
}
