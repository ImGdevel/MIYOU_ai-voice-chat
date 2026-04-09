package com.miyou.app.infrastructure.memory.adapter

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.port.ConversationCounterPort
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/** Redis에서 대화 카운터를 관리하는 어댑터입니다. */
@Component
class RedisConversationCounterAdapter(
    private val redisTemplate: ReactiveRedisTemplate<String, Long>,
) : ConversationCounterPort {
    private val counterKeyPrefix = "dialogue:conversation:counter:"

    private fun keyFor(sessionId: ConversationSessionId): String = counterKeyPrefix + sessionId.value

    override fun increment(sessionId: ConversationSessionId): Mono<Long> =
        redisTemplate.opsForValue().increment(keyFor(sessionId))

    override fun get(sessionId: ConversationSessionId): Mono<Long> =
        redisTemplate.opsForValue().get(keyFor(sessionId)).defaultIfEmpty(0L)

    override fun reset(sessionId: ConversationSessionId): Mono<Void> = redisTemplate.delete(keyFor(sessionId)).then()
}
