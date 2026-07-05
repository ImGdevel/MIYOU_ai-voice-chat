package com.miyou.app.domain.auth.port

import com.miyou.app.domain.dialogue.model.UserId
import reactor.core.publisher.Mono

interface JwtValidator {
    fun validate(token: String): Mono<UserId>
}
