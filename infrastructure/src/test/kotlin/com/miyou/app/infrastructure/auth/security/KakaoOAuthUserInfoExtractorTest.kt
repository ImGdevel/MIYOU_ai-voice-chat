package com.miyou.app.infrastructure.auth.security

import com.miyou.app.domain.auth.model.Provider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("KakaoOAuthUserInfoExtractor")
class KakaoOAuthUserInfoExtractorTest {
    private val extractor = KakaoOAuthUserInfoExtractor()

    @Test
    @DisplayName("supports only kakao registrationId")
    fun supports_onlyKakao() {
        assertThat(extractor.supports("kakao")).isTrue()
        assertThat(extractor.supports("google")).isFalse()
    }

    @Test
    @DisplayName("extracts id/email/nickname from Kakao's nested attributes")
    fun extract_mapsKakaoNestedAttributes() {
        val attributes: Map<String, Any?> =
            mapOf(
                "id" to 123456789L,
                "kakao_account" to
                    mapOf(
                        "email" to "user@kakao.com",
                        "profile" to mapOf("nickname" to "카카오유저"),
                    ),
            )

        val result = extractor.extract(attributes)

        assertThat(result.provider).isEqualTo(Provider.KAKAO)
        assertThat(result.providerUserId).isEqualTo("123456789")
        assertThat(result.email).isEqualTo("user@kakao.com")
        assertThat(result.displayName).isEqualTo("카카오유저")
    }

    @Test
    @DisplayName("tolerates a missing kakao_account block (email consent not granted)")
    fun extract_tolerantOfMissingAccountBlock() {
        val attributes: Map<String, Any?> = mapOf("id" to 999L)

        val result = extractor.extract(attributes)

        assertThat(result.providerUserId).isEqualTo("999")
        assertThat(result.email).isNull()
        assertThat(result.displayName).isNull()
    }
}
