package com.miyou.app.domain.auth.model

data class AuthenticatedUser(
    val userId: String,
    val provider: Provider? = null,
)
