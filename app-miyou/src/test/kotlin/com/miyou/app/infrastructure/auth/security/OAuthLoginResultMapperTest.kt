package com.miyou.app.infrastructure.auth.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OAuthLoginResultMapper")
class OAuthLoginResultMapperTest {
    private val mapper =
        OAuthLoginResultMapper(
            listOf(
                GoogleOAuthUserInfoExtractor(),
                KakaoOAuthUserInfoExtractor(),
                NaverOAuthUserInfoExtractor(),
            ),
        )

    @Test
    @DisplayName("dispatches to the extractor matching the registrationId")
    fun from_dispatchesToMatchingExtractor() {
        val googleResult = mapper.from("google", mapOf("sub" to "g-1", "email" to null, "name" to null))
        val kakaoResult = mapper.from("kakao", mapOf("id" to 1L))
        val naverResult = mapper.from("naver", mapOf("response" to mapOf("id" to "n-1")))

        assertThat(googleResult.providerUserId).isEqualTo("g-1")
        assertThat(kakaoResult.providerUserId).isEqualTo("1")
        assertThat(naverResult.providerUserId).isEqualTo("n-1")
    }

    @Test
    @DisplayName("throws for an unregistered provider")
    fun from_unsupportedProvider_throws() {
        org.assertj.core.api.Assertions
            .assertThatThrownBy { mapper.from("github", emptyMap()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("github")
    }
}
