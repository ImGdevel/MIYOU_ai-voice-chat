package com.miyou.app.domain.auth.port

import reactor.core.publisher.Mono

interface JwtValidator {
    fun validate(token: String): Mono<String>
}
