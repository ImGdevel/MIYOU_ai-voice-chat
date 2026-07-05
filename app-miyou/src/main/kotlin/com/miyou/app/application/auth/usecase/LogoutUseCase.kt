package com.miyou.app.application.auth.usecase

import reactor.core.publisher.Mono

interface LogoutUseCase {
    fun logout(refreshTokenValue: String): Mono<Void>
}
