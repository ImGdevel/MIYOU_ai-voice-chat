package com.miyou.app.api.auth

import com.miyou.app.api.auth.dto.AuthTokenResponse
import com.miyou.app.api.auth.dto.RefreshTokenRequest
import com.miyou.app.application.auth.usecase.LogoutUseCase
import com.miyou.app.application.auth.usecase.TokenRefreshUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/auth")
class AuthController(
    private val tokenRefreshUseCase: TokenRefreshUseCase,
    private val logoutUseCase: LogoutUseCase,
) {
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
    ): Mono<AuthTokenResponse> =
        tokenRefreshUseCase
            .refresh(request.refreshToken)
            .map(AuthTokenResponse::from)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid @RequestBody request: RefreshTokenRequest,
    ): Mono<Void> = logoutUseCase.logout(request.refreshToken)
}
