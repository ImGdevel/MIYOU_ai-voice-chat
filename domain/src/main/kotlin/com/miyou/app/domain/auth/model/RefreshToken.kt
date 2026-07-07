package com.miyou.app.domain.auth.model

import java.time.Instant
import java.util.UUID

data class RefreshToken(
    val tokenId: String,
    val userId: String,
    val issuedAt: Instant,
) {
    companion object {
        @JvmStatic
        fun issue(userId: String): RefreshToken =
            RefreshToken(
                tokenId = UUID.randomUUID().toString(),
                userId = userId,
                issuedAt = Instant.now(),
            )
    }
}
