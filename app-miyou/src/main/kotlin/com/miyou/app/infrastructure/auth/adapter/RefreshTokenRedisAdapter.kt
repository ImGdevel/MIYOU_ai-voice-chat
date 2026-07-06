package com.miyou.app.infrastructure.auth.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.miyou.app.domain.auth.model.RefreshToken
import com.miyou.app.domain.auth.port.RefreshTokenRepository
import com.miyou.app.infrastructure.auth.config.JwtProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

@Component
class RefreshTokenRedisAdapter(
    @Qualifier("reactiveRedisStringTemplate") private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val jwtProperties: JwtProperties,
) : RefreshTokenRepository {
    private val keyPrefix = "auth:refresh:"

    private fun keyFor(tokenId: String) = keyPrefix + tokenId

    private data class StoredValue(
        val userId: String,
        val issuedAt: Instant,
    )

    override fun save(token: RefreshToken): Mono<RefreshToken> {
        val value = objectMapper.writeValueAsString(StoredValue(token.userId, token.issuedAt))
        return redisTemplate
            .opsForValue()
            .set(keyFor(token.tokenId), value, Duration.ofDays(jwtProperties.refreshTokenTtlDays))
            .thenReturn(token)
    }

    override fun findByTokenId(tokenId: String): Mono<RefreshToken> =
        redisTemplate
            .opsForValue()
            .get(keyFor(tokenId))
            .map { json ->
                val stored: StoredValue = objectMapper.readValue(json)
                RefreshToken(tokenId, stored.userId, stored.issuedAt)
            }

    override fun deleteByTokenId(tokenId: String): Mono<Void> = redisTemplate.delete(keyFor(tokenId)).then()
}
