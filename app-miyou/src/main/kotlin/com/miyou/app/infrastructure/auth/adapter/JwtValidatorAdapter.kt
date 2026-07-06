package com.miyou.app.infrastructure.auth.adapter

import com.miyou.app.domain.auth.port.JwtValidator
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class JwtValidatorAdapter(
    private val reactiveJwtDecoder: ReactiveJwtDecoder,
) : JwtValidator {
    override fun validate(token: String): Mono<String> =
        reactiveJwtDecoder
            .decode(token)
            .map { jwt -> jwt.subject }
            .onErrorResume { Mono.empty() }
}
