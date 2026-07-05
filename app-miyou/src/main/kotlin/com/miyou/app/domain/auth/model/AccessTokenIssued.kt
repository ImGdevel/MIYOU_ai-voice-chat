package com.miyou.app.domain.auth.model

import java.time.Instant

data class AccessTokenIssued(
    val token: String,
    val expiresAt: Instant,
)
