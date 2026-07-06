package com.miyou.app.infrastructure.auth.adapter

import com.miyou.app.domain.auth.model.AccessTokenIssued
import com.miyou.app.domain.auth.port.JwtIssuer
import com.miyou.app.infrastructure.auth.config.JwtProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class JwtIssuerAdapter(
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties,
) : JwtIssuer {
    override fun issueAccessToken(userId: String): AccessTokenIssued {
        val now = Instant.now()
        val expiresAt = now.plus(jwtProperties.accessTokenTtlMinutes, ChronoUnit.MINUTES)
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(jwtProperties.issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(userId)
                .claim("token_type", "access")
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
        return AccessTokenIssued(jwt.tokenValue, expiresAt)
    }
}
