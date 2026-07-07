package com.miyou.app.infrastructure.auth.security

import com.miyou.app.domain.auth.model.OAuthLoginResult
import org.springframework.stereotype.Component

@Component
class OAuthLoginResultMapper(
    private val extractors: List<OAuthUserInfoExtractor>,
) {
    fun from(
        registrationId: String,
        attributes: Map<String, Any?>,
    ): OAuthLoginResult =
        extractors
            .firstOrNull { it.supports(registrationId) }
            ?.extract(attributes)
            ?: error("Unsupported OAuth2 provider: $registrationId")
}
