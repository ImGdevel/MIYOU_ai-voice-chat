package com.miyou.app.domain.auth.model

data class OAuthLoginResult(
    val provider: Provider,
    val providerUserId: String,
    val email: String?,
    val displayName: String?,
) {
    init {
        require(providerUserId.isNotBlank()) { "providerUserId cannot be blank" }
    }
}
