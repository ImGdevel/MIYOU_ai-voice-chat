package com.miyou.app.infrastructure.inbound.web.auth.dto

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank
    val refreshToken: String,
)
