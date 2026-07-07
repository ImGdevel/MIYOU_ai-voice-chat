package com.miyou.app.infrastructure.auth.security

import com.miyou.app.domain.auth.model.AuthenticatedUser
import com.miyou.app.domain.auth.port.JwtValidator
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * "선택적 강화" 필터 — Authorization: Bearer 토큰이 있고 유효하면 보안 컨텍스트를
 * 채우지만, 없거나 무효해도 절대 요청을 막지 않는다. 이 때문에 기존 익명(디바이스
 * 핑거프린트 userId) 흐름과 새 JWT 인증 흐름이 같은 엔드포인트에서 공존한다.
 *
 * 일부러 @Component가 아니다 — WebFilter 구현체는 Boot의 @WebFluxTest 슬라이스가
 * 기본 포함 대상으로 취급해서, 독립 스캔되는 @Component로 두면 CreditController/
 * MissionController 등 무관한 컨트롤러의 @WebFluxTest에도 끌려 들어가 JwtValidator
 * 의존성을 못 찾고 깨진다. SecurityConfig의 @Bean 메서드로만 생성되게 해서
 * (SecurityConfig 자체는 @WebFluxTest 기본 포함 대상이 아님) 이 문제를 피한다.
 */
class JwtAuthenticationWebFilter(
    private val jwtValidator: JwtValidator,
) : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val token = extractBearerToken(exchange) ?: return chain.filter(exchange)

        return jwtValidator
            .validate(token)
            .flatMap { userId ->
                val authentication = UsernamePasswordAuthenticationToken(AuthenticatedUser(userId), null, emptyList())
                chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            }.switchIfEmpty(Mono.defer { chain.filter(exchange) })
    }

    private fun extractBearerToken(exchange: ServerWebExchange): String? {
        val header = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith("Bearer ", ignoreCase = true)) return null
        return header.substring(7).trim().ifBlank { null }
    }
}
