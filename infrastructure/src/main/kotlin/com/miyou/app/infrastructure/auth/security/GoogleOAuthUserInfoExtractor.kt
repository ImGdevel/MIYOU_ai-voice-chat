package com.miyou.app.infrastructure.auth.security

import com.miyou.app.domain.auth.model.OAuthLoginResult
import com.miyou.app.domain.auth.model.Provider
import org.springframework.stereotype.Component

@Component
class GoogleOAuthUserInfoExtractor : OAuthUserInfoExtractor {
    override fun supports(registrationId: String): Boolean = registrationId == "google"

    override fun extract(attributes: Map<String, Any?>): OAuthLoginResult =
        OAuthLoginResult(
            provider = Provider.GOOGLE,
            providerUserId = attributes["sub"]?.toString() ?: "",
            email = attributes["email"] as? String,
            displayName = attributes["name"] as? String,
        )
}
