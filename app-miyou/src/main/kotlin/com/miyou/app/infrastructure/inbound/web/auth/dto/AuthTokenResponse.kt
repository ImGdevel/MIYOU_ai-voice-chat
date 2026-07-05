package com.miyou.app.infrastructure.inbound.web.auth.dto

import com.miyou.app.domain.auth.model.AuthTokens
import java.time.Instant

data class AuthTokenResponse(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
) {
    companion object {
        fun from(tokens: AuthTokens): AuthTokenResponse =
            AuthTokenResponse(tokens.accessToken, tokens.accessTokenExpiresAt, tokens.refreshToken)
    }
}
