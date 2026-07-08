package com.miyou.app.api.auth

import com.miyou.app.api.auth.dto.RefreshTokenRequest
import com.miyou.app.application.auth.usecase.LogoutUseCase
import com.miyou.app.application.auth.usecase.TokenRefreshUseCase
import com.miyou.app.domain.auth.exception.InvalidRefreshTokenException
import com.miyou.app.domain.auth.model.AuthTokens
import com.miyou.app.support.PermitAllSecurityTestConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant

@Import(PermitAllSecurityTestConfig::class)
@WebFluxTest(AuthController::class)
class AuthControllerTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var tokenRefreshUseCase: TokenRefreshUseCase

    @MockitoBean
    private lateinit var logoutUseCase: LogoutUseCase

    @Test
    @DisplayName("유효한 리프레시 토큰에 대해 새로운 토큰 쌍을 반환한다")
    fun refresh_returnsNewTokenPair() {
        // given
        val tokens = AuthTokens("new-access-token", Instant.now().plusSeconds(900), "new-refresh-token")
        `when`(tokenRefreshUseCase.refresh("old-refresh-token")).thenReturn(Mono.just(tokens))

        // when & then
        webTestClient
            .post()
            .uri("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RefreshTokenRequest("old-refresh-token"))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.accessToken")
            .isEqualTo("new-access-token")
            .jsonPath("$.refreshToken")
            .isEqualTo("new-refresh-token")
    }

    @Test
    @DisplayName("유효하지 않거나 만료된 리프레시 토큰의 경우 401 에러를 반환한다")
    fun refresh_invalidToken_returns401() {
        // given: 유효하지 않은 리프레시 토큰일 때 InvalidRefreshTokenException 에러를 던지도록 모킹
        `when`(tokenRefreshUseCase.refresh("bad-token"))
            .thenReturn(Mono.error(InvalidRefreshTokenException("bad-token")))

        // when & then
        webTestClient
            .post()
            .uri("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RefreshTokenRequest("bad-token"))
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    @DisplayName("로그아웃 요청 시 204 상태코드를 반환하고 로그아웃 유스케이스를 실행한다")
    fun logout_returns204() {
        // given
        `when`(logoutUseCase.logout("token-1")).thenReturn(Mono.empty())

        // when & then
        webTestClient
            .post()
            .uri("/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RefreshTokenRequest("token-1"))
            .exchange()
            .expectStatus()
            .isNoContent
    }
}
