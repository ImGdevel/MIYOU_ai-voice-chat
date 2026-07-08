package com.miyou.app.infrastructure.auth.adapter

import com.miyou.app.infrastructure.auth.config.JwtConfig
import com.miyou.app.infrastructure.auth.config.JwtProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("JWT 발급 어댑터 (JwtIssuerAdapter)")
class JwtIssuerAdapterTest {
    private val properties =
        JwtProperties().apply {
            secret = "test-secret-must-be-at-least-32-bytes-long-for-hs256"
            accessTokenTtlMinutes = 15
            issuer = "miyou-app-test"
        }
    private val adapter = JwtIssuerAdapter(JwtConfig(properties).jwtEncoder(), properties)

    @Test
    @DisplayName("미래 만료 시점을 가진 토큰을 발급한다")
    fun issueAccessToken_setsFutureExpiry() {
        // given: 토큰 발급 직전 시간 기록
        val before = Instant.now()

        // when: 토큰 발급
        val issued = adapter.issueAccessToken("issuer-test-user")

        // then: 토큰 생성 여부 및 만료 시간 검증
        assertThat(issued.token).isNotBlank()
        assertThat(issued.expiresAt).isAfter(before)
    }
}
