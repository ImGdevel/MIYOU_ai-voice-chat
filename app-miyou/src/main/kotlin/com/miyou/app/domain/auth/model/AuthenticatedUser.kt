package com.miyou.app.domain.auth.model

import com.miyou.app.domain.dialogue.model.UserId

data class AuthenticatedUser(
    val userId: UserId,
    val provider: Provider? = null,
)
