package com.miyou.app.infrastructure.auth.security

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
class OAuthAuthenticationFailureHandler : ServerAuthenticationFailureHandler {
    override fun onAuthenticationFailure(
        webFilterExchange: WebFilterExchange,
        exception: AuthenticationException,
    ): Mono<Void> {
        val response = webFilterExchange.exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON
        val message = (exception.message ?: "unknown").replace("\"", "'")
        val body = """{"error":"oauth2_login_failed","message":"$message"}"""
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.toByteArray(StandardCharsets.UTF_8))))
    }
}
