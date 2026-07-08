package com.miyou.app.infrastructure.auth.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import reactor.core.publisher.Mono

/**
 * Kakao/Naver/Google OAuth2 클라이언트 수동 등록 설정.
 * 설정된 Provider가 없으면 앱 기동을 위해 더미 Repository를 반환합니다.
 */
@Configuration
class ClientRegistrationConfig(
    @Value("\${app.oauth2.google.client-id:}") private val googleClientId: String,
    @Value("\${app.oauth2.google.client-secret:}") private val googleClientSecret: String,
    @Value("\${app.oauth2.kakao.client-id:}") private val kakaoClientId: String,
    @Value("\${app.oauth2.kakao.client-secret:}") private val kakaoClientSecret: String,
    @Value("\${app.oauth2.naver.client-id:}") private val naverClientId: String,
    @Value("\${app.oauth2.naver.client-secret:}") private val naverClientSecret: String,
    @Value("\${app.oauth2.redirect-base-uri:http://localhost:8081}") private val redirectBaseUri: String,
) {
    // OAuth2 로그인/콜백은 WebFilter 레이어에서 처리되므로 API prefix를 적용하지 않음.

    @Bean
    fun clientRegistrationRepository(): ReactiveClientRegistrationRepository {
        val registrations =
            buildList {
                if (googleClientId.isNotBlank()) add(googleRegistration())
                if (kakaoClientId.isNotBlank()) add(kakaoRegistration())
                if (naverClientId.isNotBlank()) add(naverRegistration())
            }
        if (registrations.isEmpty()) return ReactiveClientRegistrationRepository { Mono.empty() }
        return InMemoryReactiveClientRegistrationRepository(registrations)
    }

    private fun googleRegistration(): ClientRegistration =
        CommonOAuth2Provider.GOOGLE
            .getBuilder("google")
            .clientId(googleClientId)
            .clientSecret(googleClientSecret)
            .redirectUri("$redirectBaseUri/login/oauth2/code/google")
            .build()

    private fun kakaoRegistration(): ClientRegistration =
        ClientRegistration
            .withRegistrationId("kakao")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .scope("profile_nickname", "account_email")
            .authorizationUri("https://kauth.kakao.com/oauth/authorize")
            .tokenUri("https://kauth.kakao.com/oauth/token")
            .userInfoUri("https://kapi.kakao.com/v2/user/me")
            .userNameAttributeName("id")
            .redirectUri("$redirectBaseUri/login/oauth2/code/kakao")
            .clientId(kakaoClientId)
            .clientSecret(kakaoClientSecret)
            .clientName("Kakao")
            .build()

    // Naver의 사용자 정보 응답 구조에 맞춰 userNameAttributeName을 "response"로 설정.
    private fun naverRegistration(): ClientRegistration =
        ClientRegistration
            .withRegistrationId("naver")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .scope("name", "email")
            .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
            .tokenUri("https://nid.naver.com/oauth2.0/token")
            .userInfoUri("https://openapi.naver.com/v1/nid/me")
            .userNameAttributeName("response")
            .redirectUri("$redirectBaseUri/login/oauth2/code/naver")
            .clientId(naverClientId)
            .clientSecret(naverClientSecret)
            .clientName("Naver")
            .build()
}
