package com.miyou.app.domain.auth.exception

class InvalidRefreshTokenException(
    val tokenId: String,
) : RuntimeException("Invalid or expired refresh token: tokenId=$tokenId")
