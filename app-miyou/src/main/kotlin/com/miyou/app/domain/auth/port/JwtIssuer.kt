package com.miyou.app.domain.auth.port

import com.miyou.app.domain.auth.model.AccessTokenIssued
import com.miyou.app.domain.dialogue.model.UserId

interface JwtIssuer {
    fun issueAccessToken(userId: UserId): AccessTokenIssued
}
