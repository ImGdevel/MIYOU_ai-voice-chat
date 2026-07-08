package com.miyou.app.api.auth.dto

import com.miyou.app.domain.auth.model.AuthTokens
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "인증 토큰 응답 DTO")
data class AuthTokenResponse(
    @field:Schema(
        description = "JWT 액세스 토큰 (API 요청 시 Authorization 헤더에 Bearer 토큰으로 사용)",
        example = "eyJhbGciOi...eyJzdWIiOiJ1c2VyLTEyMyJ9.sig"
    )
    val accessToken: String,
    @field:Schema(description = "액세스 토큰 만료 시각", example = "2026-07-08T15:30:00Z")
    val accessTokenExpiresAt: Instant,
    @field:Schema(
        description = "JWT 리프레시 토큰 (액세스 토큰 갱신용)",
        example = "eyJhbGciOi...eyJzdWIiOiJ1c2VyLTEyMyJ9.sig"
    )
    val refreshToken: String,
) {
    companion object {
        fun from(tokens: AuthTokens): AuthTokenResponse =
            AuthTokenResponse(tokens.accessToken, tokens.accessTokenExpiresAt, tokens.refreshToken)
    }
}
