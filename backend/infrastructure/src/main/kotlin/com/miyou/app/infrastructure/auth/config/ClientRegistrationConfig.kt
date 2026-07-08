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
 * Kakao/Naver는 Spring Security의 CommonOAuth2Provider에 없어 수동 등록한다.
 * Google은 표준 OIDC라 CommonOAuth2Provider.GOOGLE로 충분.
 *
 * client-id가 비어있는 provider는 등록에서 제외한다 — 아직 credential을 발급받지
 * 않은 로컬/CI 환경에서도 앱이 정상 기동해야 한다(익명 흐름은 이 기능과 무관하게
 * 계속 동작해야 함). spring-boot-starter-oauth2-client가 클래스패스에 있으면
 * ReactiveOAuth2AuthorizedClientManager 자동 설정이 이 빈의 존재 자체를 무조건
 * 요구하므로(oauth2Login() 호출 여부와 무관하게), null을 반환할 수 없다 —
 * InMemoryReactiveClientRegistrationRepository는 빈 리스트를 거부하므로, 설정된
 * provider가 하나도 없을 땐 항상 빈 Mono만 반환하는 무해한 더미 구현으로 대체한다.
 * (SecurityConfig는 별도로 client-id 존재 여부를 직접 확인해 oauth2Login() 활성화를 판단한다.)
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
    // OAuth2 로그인/콜백은 app.api.prefix(ApiPathPrefixConfiguration)의 영향을 받지 않는다 -
    // Spring Security의 WebFilter는 @RestController 핸들러 매핑과 별개 레이어라
    // addPathPrefix로 옮겨지지 않는다. 그래서 여기서 prefix를 붙이지 않는다.

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

    // Naver 응답은 { response: { id, email, name } } 형태라 userNameAttributeName을
    // "response"로 잡아야 함 — 실제 라이브 계정으로 스모크 테스트 필요 (알려진 리스크 지점).
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
