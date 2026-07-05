package com.miyou.app.infrastructure.auth.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import javax.crypto.spec.SecretKeySpec

/**
 * HS256 대칭키로 시작 — 단일 백엔드 서비스라 충분함.
 * 여러 서비스가 토큰을 검증해야 하는 시점이 오면 이 클래스의 두 빈만
 * RSA/JWKSource 기반으로 교체하면 되고, domain/application 레이어는
 * 영향받지 않는다 (JwtIssuer/JwtValidator 포트 뒤에 알고리즘이 숨어 있음).
 */
@Configuration
class JwtConfig(
    private val jwtProperties: JwtProperties,
) {
    private fun secretKey(): SecretKeySpec {
        require(jwtProperties.secret.isNotBlank()) { "jwt.secret must be configured" }
        return SecretKeySpec(jwtProperties.secret.toByteArray(), "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(secretKey()))

    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey()).build()
}
