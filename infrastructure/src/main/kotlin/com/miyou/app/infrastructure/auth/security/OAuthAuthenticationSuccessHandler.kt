package com.miyou.app.infrastructure.auth.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.miyou.app.application.auth.usecase.OAuthLoginUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * 백엔드 전용 라운드라 프론트 콜백 타겟이 없음 — 토큰 쌍을 리다이렉트 없이
 * JSON body로 직접 반환한다 (curl/브라우저로 바로 확인 가능). 프론트 연동 시
 * 리다이렉트+원타임코드 교환 방식으로 바뀔 수 있음.
 */
@Component
class OAuthAuthenticationSuccessHandler(
    private val oAuthLoginUseCase: OAuthLoginUseCase,
    private val oAuthLoginResultMapper: OAuthLoginResultMapper,
    private val objectMapper: ObjectMapper,
) : ServerAuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        webFilterExchange: WebFilterExchange,
        authentication: Authentication,
    ): Mono<Void> {
        val token = authentication as OAuth2AuthenticationToken
        val registrationId = token.authorizedClientRegistrationId
        val attributes = token.principal.attributes
        return Mono
            .fromCallable { oAuthLoginResultMapper.from(registrationId, attributes) }
            .flatMap { loginResult -> oAuthLoginUseCase.loginOrRegister(loginResult) }
            .flatMap { authTokens ->
                val body = objectMapper.writeValueAsBytes(authTokens)
                val response = webFilterExchange.exchange.response
                response.statusCode = HttpStatus.OK
                response.headers.contentType = MediaType.APPLICATION_JSON
                response.writeWith(Mono.just(response.bufferFactory().wrap(body)))
            }
    }
}
