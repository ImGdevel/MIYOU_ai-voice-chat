package com.miyou.app.infrastructure.auth.adapter

import com.miyou.app.infrastructure.auth.config.JwtConfig
import com.miyou.app.infrastructure.auth.config.JwtProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

@DisplayName("JWT 검증 어댑터 (JwtValidatorAdapter)")
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
    @DisplayName("토큰 발급 및 검증: 갓 발급된 토큰을 검증하면 동일한 userId를 리턴한다")
    fun validate_roundTrip_returnsIssuedUserId() {
        // given: 사용자 ID 설정 및 토큰 발급
        val userId = "validator-round-trip-user"
        val issued = issuer.issueAccessToken(userId)

        // when & then: 토큰 검증 시 발급된 사용자 ID가 일치하는지 확인
        StepVerifier
            .create(validator.validate(issued.token))
            .assertNext { validated -> assertThat(validated).isEqualTo(userId) }
            .verifyComplete()
    }

    @Test
    @DisplayName("잘못된 형식의 쓰레기 토큰은 에러 없이 빈 결과를 반환한다 (silently)")
    fun validate_garbageToken_returnsEmpty() {
        // when & then: 유효하지 않은 임의의 문자열 검증 시 빈 Mono 반환 확인
        StepVerifier.create(validator.validate("not-a-real-jwt")).verifyComplete()
    }

    @Test
    @DisplayName("다른 비밀 키로 서명된 토큰은 에러 없이 빈 결과를 반환한다")
    fun validate_wrongSecret_returnsEmpty() {
        // given: 다른 비밀 키 설정을 가진 별도 어댑터로 토큰 생성
        val otherProperties =
            JwtProperties().apply {
                secret = "a-completely-different-secret-that-is-also-32-bytes"
                accessTokenTtlMinutes = 15
                issuer = "miyou-app-test"
            }
        val otherIssuer = JwtIssuerAdapter(JwtConfig(otherProperties).jwtEncoder(), otherProperties)
        val tokenFromOtherSecret = otherIssuer.issueAccessToken("wrong-secret-user").token

        // when & then: 다른 비밀 키로 발급된 토큰을 현재 비밀 키를 사용하는 어댑터로 검증 시 빈 결과 확인
        StepVerifier.create(validator.validate(tokenFromOtherSecret)).verifyComplete()
    }
}
