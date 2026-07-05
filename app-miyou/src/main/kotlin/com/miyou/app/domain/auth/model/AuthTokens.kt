package com.miyou.app.domain.auth.model

import java.time.Instant

data class AuthTokens(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
)
