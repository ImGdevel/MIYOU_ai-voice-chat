package com.miyou.app.domain.auth.model

import java.time.Instant
import java.util.UUID

data class OAuthAccount(
    val id: String,
    val provider: Provider,
    val providerUserId: String,
    val userId: String,
    val email: String?,
    val displayName: String?,
    val createdAt: Instant? = null,
) {
    fun withCreatedAtOrNow(): OAuthAccount = if (createdAt == null) copy(createdAt = Instant.now()) else this

    companion object {
        @JvmStatic
        fun create(
            loginResult: OAuthLoginResult,
            userId: String,
        ): OAuthAccount =
            OAuthAccount(
                id = UUID.randomUUID().toString(),
                provider = loginResult.provider,
                providerUserId = loginResult.providerUserId,
                userId = userId,
                email = loginResult.email,
                displayName = loginResult.displayName,
            ).withCreatedAtOrNow()
    }
}
