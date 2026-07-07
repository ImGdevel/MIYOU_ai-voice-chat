package com.miyou.app.domain.auth.port

import com.miyou.app.domain.auth.model.AccessTokenIssued

interface JwtIssuer {
    fun issueAccessToken(userId: String): AccessTokenIssued
}
