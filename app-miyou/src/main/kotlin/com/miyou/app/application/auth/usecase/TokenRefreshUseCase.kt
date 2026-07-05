package com.miyou.app.application.auth.usecase

import com.miyou.app.domain.auth.model.AuthTokens
import reactor.core.publisher.Mono

interface TokenRefreshUseCase {
    fun refresh(refreshTokenValue: String): Mono<AuthTokens>
}
