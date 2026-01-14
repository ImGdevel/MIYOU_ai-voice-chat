package com.study.webflux.rag.infrastructure.memory.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.memory.port.ConversationCounterPort;
import reactor.core.publisher.Mono;

/** Redis에서 대화 카운터를 관리하는 어댑터입니다. */
@Component
@RequiredArgsConstructor
public class RedisConversationCounterAdapter implements ConversationCounterPort {

	private static final String COUNTER_KEY_PREFIX = "dialogue:conversation:counter:";

	private final ReactiveRedisTemplate<String, Long> redisTemplate;

	private String keyFor(ConversationSessionId sessionId) {
		return COUNTER_KEY_PREFIX + sessionId.value();
	}

	@Override
	public Mono<Long> increment(ConversationSessionId sessionId) {
		return redisTemplate.opsForValue().increment(keyFor(sessionId));
	}

	@Override
	public Mono<Long> get(ConversationSessionId sessionId) {
		return redisTemplate.opsForValue().get(keyFor(sessionId)).defaultIfEmpty(0L);
	}

	@Override
	public Mono<Void> reset(ConversationSessionId sessionId) {
		return redisTemplate.delete(keyFor(sessionId)).then();
	}
}
