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
    @DisplayName("refresh returns a new token pair for a valid refresh token")
    fun refresh_returnsNewTokenPair() {
        val tokens = AuthTokens("new-access-token", Instant.now().plusSeconds(900), "new-refresh-token")

        `when`(tokenRefreshUseCase.refresh("old-refresh-token")).thenReturn(Mono.just(tokens))

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
    @DisplayName("refresh returns 401 for an invalid or expired refresh token")
    fun refresh_invalidToken_returns401() {
        `when`(tokenRefreshUseCase.refresh("bad-token"))
            .thenReturn(Mono.error(InvalidRefreshTokenException("bad-token")))

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
    @DisplayName("logout returns 204 and delegates to the use case")
    fun logout_returns204() {
        `when`(logoutUseCase.logout("token-1")).thenReturn(Mono.empty())

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
