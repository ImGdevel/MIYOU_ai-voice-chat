package com.miyou.app.infrastructure.auth.document

import com.miyou.app.domain.auth.model.OAuthAccount
import com.miyou.app.domain.auth.model.Provider
import com.miyou.app.domain.dialogue.model.UserId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "oauth_accounts")
@CompoundIndex(name = "provider_provider_user_id_idx", def = "{'provider': 1, 'providerUserId': 1}", unique = true)
data class OAuthAccountDocument(
    @Id val id: String,
    val provider: String,
    val providerUserId: String,
    @Indexed val userId: String,
    val email: String?,
    val displayName: String?,
    val createdAt: Instant,
) {
    companion object {
        fun fromDomain(account: OAuthAccount): OAuthAccountDocument =
            OAuthAccountDocument(
                account.id,
                account.provider.name,
                account.providerUserId,
                account.userId.value,
                account.email,
                account.displayName,
                account.createdAt ?: Instant.now(),
            )
    }

    fun toDomain(): OAuthAccount =
        OAuthAccount(
            id,
            Provider.valueOf(provider),
            providerUserId,
            UserId.of(userId),
            email,
            displayName,
            createdAt,
        )
}
