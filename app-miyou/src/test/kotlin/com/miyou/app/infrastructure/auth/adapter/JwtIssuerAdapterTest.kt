package com.miyou.app.infrastructure.auth.adapter

import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.infrastructure.auth.config.JwtConfig
import com.miyou.app.infrastructure.auth.config.JwtProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("JwtIssuerAdapter")
class JwtIssuerAdapterTest {
    private val properties =
        JwtProperties().apply {
            secret = "test-secret-must-be-at-least-32-bytes-long-for-hs256"
            accessTokenTtlMinutes = 15
            issuer = "miyou-app-test"
        }
    private val adapter = JwtIssuerAdapter(JwtConfig(properties).jwtEncoder(), properties)

    @Test
    @DisplayName("issues a token with a future expiry")
    fun issueAccessToken_setsFutureExpiry() {
        val before = Instant.now()

        val issued = adapter.issueAccessToken(UserId.of("issuer-test-user"))

        assertThat(issued.token).isNotBlank()
        assertThat(issued.expiresAt).isAfter(before)
    }
}
