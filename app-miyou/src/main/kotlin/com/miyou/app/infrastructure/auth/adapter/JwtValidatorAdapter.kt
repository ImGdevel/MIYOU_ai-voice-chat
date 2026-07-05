package com.miyou.app.infrastructure.auth.adapter

import com.miyou.app.domain.auth.port.JwtValidator
import com.miyou.app.domain.dialogue.model.UserId
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class JwtValidatorAdapter(
    private val reactiveJwtDecoder: ReactiveJwtDecoder,
) : JwtValidator {
    override fun validate(token: String): Mono<UserId> =
        reactiveJwtDecoder
            .decode(token)
            .map { jwt -> UserId.of(jwt.subject) }
            .onErrorResume { Mono.empty() }
}
