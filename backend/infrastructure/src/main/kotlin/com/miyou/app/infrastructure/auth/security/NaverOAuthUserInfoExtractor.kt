package com.miyou.app.infrastructure.auth.security

import com.miyou.app.domain.auth.model.OAuthLoginResult
import com.miyou.app.domain.auth.model.Provider
import org.springframework.stereotype.Component

@Component
class NaverOAuthUserInfoExtractor : OAuthUserInfoExtractor {
    override fun supports(registrationId: String): Boolean = registrationId == "naver"

    override fun extract(attributes: Map<String, Any?>): OAuthLoginResult {
        val response = attributes["response"] as? Map<*, *>
        return OAuthLoginResult(
            provider = Provider.NAVER,
            providerUserId = response?.get("id") as? String ?: "",
            email = response?.get("email") as? String,
            displayName = response?.get("name") as? String,
        )
    }
}
