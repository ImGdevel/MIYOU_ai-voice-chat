package com.miyou.app.application.auth.usecase

import com.miyou.app.domain.auth.model.AuthTokens
import com.miyou.app.domain.auth.model.OAuthLoginResult
import reactor.core.publisher.Mono

interface OAuthLoginUseCase {
    fun loginOrRegister(result: OAuthLoginResult): Mono<AuthTokens>
}
