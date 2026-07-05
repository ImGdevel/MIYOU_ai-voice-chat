package com.miyou.app.infrastructure.auth.security

import com.miyou.app.domain.auth.model.Provider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("NaverOAuthUserInfoExtractor")
class NaverOAuthUserInfoExtractorTest {
    private val extractor = NaverOAuthUserInfoExtractor()

    @Test
    @DisplayName("supports only naver registrationId")
    fun supports_onlyNaver() {
        assertThat(extractor.supports("naver")).isTrue()
        assertThat(extractor.supports("kakao")).isFalse()
    }

    @Test
    @DisplayName("extracts id/email/name from Naver's response wrapper")
    fun extract_mapsNaverResponseWrapper() {
        val attributes: Map<String, Any?> =
            mapOf(
                "response" to
                    mapOf(
                        "id" to "naver-user-123",
                        "email" to "user@naver.com",
                        "name" to "김철수",
                    ),
            )

        val result = extractor.extract(attributes)

        assertThat(result.provider).isEqualTo(Provider.NAVER)
        assertThat(result.providerUserId).isEqualTo("naver-user-123")
        assertThat(result.email).isEqualTo("user@naver.com")
        assertThat(result.displayName).isEqualTo("김철수")
    }

    @Test
    @DisplayName("rejects a missing response wrapper (OAuthLoginResult requires a non-blank providerUserId)")
    fun extract_missingResponseWrapper_throws() {
        org.assertj.core.api.Assertions
            .assertThatThrownBy { extractor.extract(emptyMap()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
