package com.miyou.app.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * @WebFluxTest 슬라이스용 permitAll 체인 — 실제 SecurityConfig는 ClientRegistrationConfig/
 * OAuthAuthenticationSuccessHandler 등 무거운 의존성 때문에 좁은 슬라이스에 못 들어온다.
 * 이 빈이 없으면 spring-security-config가 클래스패스에 있다는 이유만으로 Boot의
 * ReactiveSecurityAutoConfiguration이 deny-by-default 체인을 기본 등록해 기존
 * 익명 흐름 테스트가 401/403으로 깨진다. @ConditionalOnMissingBean(SecurityWebFilterChain)
 * 조건에 걸리는 이 빈만 채워주면 Boot 기본 체인은 물러나고, @AuthenticationPrincipal
 * 리졸버 등 나머지 시큐리티 인프라는 그대로 살아있다.
 */
@TestConfiguration
class PermitAllSecurityTestConfig {
    @Bean
    fun testSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .authorizeExchange { it.anyExchange().permitAll() }
            .build()
}
