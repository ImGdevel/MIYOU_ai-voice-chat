package com.miyou.app.infrastructure.auth.security

import com.miyou.app.domain.auth.model.OAuthLoginResult

interface OAuthUserInfoExtractor {
    fun supports(registrationId: String): Boolean

    fun extract(attributes: Map<String, Any?>): OAuthLoginResult
}
