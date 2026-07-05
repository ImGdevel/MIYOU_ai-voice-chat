package com.miyou.app.domain.auth.model

import com.miyou.app.domain.dialogue.model.UserId
import java.time.Instant
import java.util.UUID

data class RefreshToken(
    val tokenId: String,
    val userId: UserId,
    val issuedAt: Instant,
) {
    companion object {
        @JvmStatic
        fun issue(userId: UserId): RefreshToken =
            RefreshToken(
                tokenId = UUID.randomUUID().toString(),
                userId = userId,
                issuedAt = Instant.now(),
            )
    }
}
