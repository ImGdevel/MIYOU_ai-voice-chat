package com.miyou.app.infrastructure.auth.config

import com.miyou.app.domain.auth.port.JwtValidator
import com.miyou.app.infrastructure.auth.security.JwtAuthenticationWebFilter
import com.miyou.app.infrastructure.auth.security.OAuthAuthenticationFailureHandler
import com.miyou.app.infrastructure.auth.security.OAuthAuthenticationSuccessHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.userinfo.DefaultReactiveOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * "선택적 강화(optional enrichment)" 보안 설정.
 * JwtAuthenticationWebFilter를 통해 인증을 처리하며, 인증되지 않은 사용자도 접근할 수 있도록 허용합니다.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val oAuthAuthenticationSuccessHandler: OAuthAuthenticationSuccessHandler,
    private val oAuthAuthenticationFailureHandler: OAuthAuthenticationFailureHandler,
    @Value("\${app.oauth2.google.client-id:}") private val googleClientId: String,
    @Value("\${app.oauth2.kakao.client-id:}") private val kakaoClientId: String,
    @Value("\${app.oauth2.naver.client-id:}") private val naverClientId: String,
) {
    @Bean
    fun jwtAuthenticationWebFilter(jwtValidator: JwtValidator): JwtAuthenticationWebFilter =
        JwtAuthenticationWebFilter(jwtValidator)

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        jwtAuthenticationWebFilter: JwtAuthenticationWebFilter,
    ): SecurityWebFilterChain {
        http
            .csrf { it.disable() }
            .authorizeExchange { it.anyExchange().permitAll() }
            .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)

        // OAuth2 설정이 유효한 경우에만 oauth2Login()을 활성화하여 로컬/CI 환경 기동 보장.
        val hasAnyOAuthProvider = listOf(googleClientId, kakaoClientId, naverClientId).any { it.isNotBlank() }
        if (hasAnyOAuthProvider) {
            http.oauth2Login { oauth2Login ->
                oauth2Login.authenticationSuccessHandler(oAuthAuthenticationSuccessHandler)
                oauth2Login.authenticationFailureHandler(oAuthAuthenticationFailureHandler)
            }
        }

        return http.build()
    }

    // WebFlux OAuth2Login의 userInfoEndpoint 빈 수동 배선을 위한 UserService Bean 정의.
    @Bean
    fun oidcUserService(): ReactiveOAuth2UserService<OidcUserRequest, OidcUser> = OidcReactiveOAuth2UserService()

    @Bean
    fun oauth2UserService(): ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> =
        DefaultReactiveOAuth2UserService()
}
