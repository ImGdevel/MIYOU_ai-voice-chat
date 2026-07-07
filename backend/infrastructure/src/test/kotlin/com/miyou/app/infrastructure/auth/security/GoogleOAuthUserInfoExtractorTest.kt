package com.miyou.app.infrastructure.auth.security

import com.miyou.app.domain.auth.model.Provider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("GoogleOAuthUserInfoExtractor")
class GoogleOAuthUserInfoExtractorTest {
    private val extractor = GoogleOAuthUserInfoExtractor()

    @Test
    @DisplayName("supports only google registrationId")
    fun supports_onlyGoogle() {
        assertThat(extractor.supports("google")).isTrue()
        assertThat(extractor.supports("kakao")).isFalse()
    }

    @Test
    @DisplayName("extracts sub/email/name from OIDC-shaped attributes")
    fun extract_mapsOidcAttributes() {
        val attributes =
            mapOf(
                "sub" to "google-user-123",
                "email" to "user@example.com",
                "name" to "홍길동",
            )

        val result = extractor.extract(attributes)

        assertThat(result.provider).isEqualTo(Provider.GOOGLE)
        assertThat(result.providerUserId).isEqualTo("google-user-123")
        assertThat(result.email).isEqualTo("user@example.com")
        assertThat(result.displayName).isEqualTo("홍길동")
    }
}
