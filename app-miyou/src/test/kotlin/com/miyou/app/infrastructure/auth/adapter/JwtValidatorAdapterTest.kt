package com.miyou.app.infrastructure.auth.adapter

import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.infrastructure.auth.config.JwtConfig
import com.miyou.app.infrastructure.auth.config.JwtProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

@DisplayName("JwtValidatorAdapter")
class JwtValidatorAdapterTest {
    private val properties =
        JwtProperties().apply {
            secret = "test-secret-must-be-at-least-32-bytes-long-for-hs256"
            accessTokenTtlMinutes = 15
            issuer = "miyou-app-test"
        }
    private val issuer = JwtIssuerAdapter(JwtConfig(properties).jwtEncoder(), properties)
    private val validator = JwtValidatorAdapter(JwtConfig(properties).reactiveJwtDecoder())

    @Test
    @DisplayName("round-trip: a freshly issued token validates back to the same userId")
    fun validate_roundTrip_returnsIssuedUserId() {
        val userId = UserId.of("validator-round-trip-user")
        val issued = issuer.issueAccessToken(userId)

        StepVerifier
            .create(validator.validate(issued.token))
            .assertNext { validated -> assertThat(validated).isEqualTo(userId) }
            .verifyComplete()
    }

    @Test
    @DisplayName("a garbage token fails validation silently (empty, not an error)")
    fun validate_garbageToken_returnsEmpty() {
        StepVerifier.create(validator.validate("not-a-real-jwt")).verifyComplete()
    }

    @Test
    @DisplayName("a token signed with a different secret fails validation silently")
    fun validate_wrongSecret_returnsEmpty() {
        val otherProperties =
            JwtProperties().apply {
                secret = "a-completely-different-secret-that-is-also-32-bytes"
                accessTokenTtlMinutes = 15
                issuer = "miyou-app-test"
            }
        val otherIssuer = JwtIssuerAdapter(JwtConfig(otherProperties).jwtEncoder(), otherProperties)
        val tokenFromOtherSecret = otherIssuer.issueAccessToken(UserId.of("wrong-secret-user")).token

        StepVerifier.create(validator.validate(tokenFromOtherSecret)).verifyComplete()
    }
}
