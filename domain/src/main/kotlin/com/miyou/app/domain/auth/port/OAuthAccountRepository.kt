package com.miyou.app.domain.auth.port

import com.miyou.app.domain.auth.model.OAuthAccount
import com.miyou.app.domain.auth.model.Provider
import reactor.core.publisher.Mono

interface OAuthAccountRepository {
    fun findByProviderAndProviderUserId(
        provider: Provider,
        providerUserId: String,
    ): Mono<OAuthAccount>

    fun save(account: OAuthAccount): Mono<OAuthAccount>
}
