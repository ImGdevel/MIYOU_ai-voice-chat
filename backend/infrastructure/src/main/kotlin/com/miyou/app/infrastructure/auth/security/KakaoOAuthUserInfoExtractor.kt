package com.miyou.app.infrastructure.auth.security

import com.miyou.app.domain.auth.model.OAuthLoginResult
import com.miyou.app.domain.auth.model.Provider
import org.springframework.stereotype.Component

@Component
class KakaoOAuthUserInfoExtractor : OAuthUserInfoExtractor {
    override fun supports(registrationId: String): Boolean = registrationId == "kakao"

    override fun extract(attributes: Map<String, Any?>): OAuthLoginResult {
        val account = attributes["kakao_account"] as? Map<*, *>
        val profile = account?.get("profile") as? Map<*, *>
        return OAuthLoginResult(
            provider = Provider.KAKAO,
            providerUserId = attributes.requiredIdOf("id"),
            email = account?.get("email") as? String,
            displayName = profile?.get("nickname") as? String,
        )
    }
}
