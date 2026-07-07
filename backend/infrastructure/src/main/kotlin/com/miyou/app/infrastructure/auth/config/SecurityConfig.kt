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
 * "선택적 강화(optional enrichment)" 설정 — oauth2ResourceServer().jwt()를 쓰지 않는다.
 * 그 DSL은 토큰 없음/무효 시 401을 던지는 deny-by-default라 "익명 요청도 그대로
 * 통과해야 한다"는 요구사항과 충돌한다. 대신 모든 경로를 permitAll로 열어두고
 * JwtAuthenticationWebFilter가 있으면 인증 컨텍스트를 채우고, 없으면 그냥 통과시킨다.
 * oauth2Login()만 예외 — OAuth2 인가/콜백 진입점 경로들은 로그인 전용이라
 * deny-by-default(Spring 기본 동작)여도 무방하다.
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

        // provider credential이 하나도 설정되지 않은 환경(로컬/CI)에서도 앱은 기동돼야
        // 한다(익명 흐름은 이 기능과 무관) — ClientRegistrationConfig의 빈은
        // ReactiveOAuth2AuthorizedClientManager 자동 설정 때문에 항상 존재해야 해서
        // (더미 구현으로라도) null 여부로는 판단할 수 없다. 대신 client-id 설정 여부를
        // 직접 확인해서 oauth2Login()을 켤지 정한다.
        val hasAnyOAuthProvider = listOf(googleClientId, kakaoClientId, naverClientId).any { it.isNotBlank() }
        if (hasAnyOAuthProvider) {
            http.oauth2Login { oauth2Login ->
                oauth2Login.authenticationSuccessHandler(oAuthAuthenticationSuccessHandler)
                oauth2Login.authenticationFailureHandler(oAuthAuthenticationFailureHandler)
            }
        }

        return http.build()
    }

    // OAuth2LoginSpec에는 WebFlux용 userInfoEndpoint() DSL이 없다 — 대신 정확한
    // 제네릭 타입의 빈을 ResolvableType으로 찾아 자동 배선한다 (Google=OIDC,
    // Kakao/Naver=순수 OAuth2).
    @Bean
    fun oidcUserService(): ReactiveOAuth2UserService<OidcUserRequest, OidcUser> = OidcReactiveOAuth2UserService()

    @Bean
    fun oauth2UserService(): ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> =
        DefaultReactiveOAuth2UserService()
}
