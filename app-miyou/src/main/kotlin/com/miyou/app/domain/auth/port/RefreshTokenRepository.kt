package com.miyou.app.domain.auth.port

import com.miyou.app.domain.auth.model.RefreshToken
import reactor.core.publisher.Mono

interface RefreshTokenRepository {
    fun save(token: RefreshToken): Mono<RefreshToken>

    fun findByTokenId(tokenId: String): Mono<RefreshToken>

    fun deleteByTokenId(tokenId: String): Mono<Void>
}
