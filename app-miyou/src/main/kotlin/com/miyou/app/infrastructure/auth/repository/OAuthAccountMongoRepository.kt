package com.miyou.app.infrastructure.auth.repository

import com.miyou.app.infrastructure.auth.document.OAuthAccountDocument
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface OAuthAccountMongoRepository : ReactiveMongoRepository<OAuthAccountDocument, String> {
    fun findByProviderAndProviderUserId(
        provider: String,
        providerUserId: String,
    ): Mono<OAuthAccountDocument>
}
