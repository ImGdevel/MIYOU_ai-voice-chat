package com.miyou.app.infrastructure.auth.adapter

import com.miyou.app.domain.auth.model.OAuthAccount
import com.miyou.app.domain.auth.model.Provider
import com.miyou.app.domain.auth.port.OAuthAccountRepository
import com.miyou.app.infrastructure.auth.document.OAuthAccountDocument
import com.miyou.app.infrastructure.auth.repository.OAuthAccountMongoRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class OAuthAccountMongoAdapter(
    private val mongoRepository: OAuthAccountMongoRepository,
) : OAuthAccountRepository {
    override fun findByProviderAndProviderUserId(
        provider: Provider,
        providerUserId: String,
    ): Mono<OAuthAccount> =
        mongoRepository
            .findByProviderAndProviderUserId(provider.name, providerUserId)
            .map(OAuthAccountDocument::toDomain)

    override fun save(account: OAuthAccount): Mono<OAuthAccount> =
        mongoRepository
            .save(OAuthAccountDocument.fromDomain(account))
            .map(OAuthAccountDocument::toDomain)
}
