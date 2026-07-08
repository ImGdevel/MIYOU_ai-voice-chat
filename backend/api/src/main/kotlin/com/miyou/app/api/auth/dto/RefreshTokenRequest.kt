package com.miyou.app.api.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "토큰 재발급 및 로그아웃 요청 DTO")
data class RefreshTokenRequest(
    @field:NotBlank
    @field:Schema(
        description = "JWT 리프레시 토큰",
        example = "eyJhbGciOi...eyJzdWIiOiJ1c2VyLTEyMyJ9.sig"
    )
    val refreshToken: String,
)
