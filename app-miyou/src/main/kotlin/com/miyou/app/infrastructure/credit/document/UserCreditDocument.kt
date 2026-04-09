package com.miyou.app.infrastructure.credit.document

import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.domain.dialogue.model.UserId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "user_credits")
data class UserCreditDocument(
    @Id val id: String,
    @Indexed(unique = true) val userId: String,
    val balance: Long,
    @Version val version: Long,
    val updatedAt: Instant,
) {
    companion object {
        fun fromDomain(credit: UserCredit): UserCreditDocument =
            UserCreditDocument(
                credit.userId().value(),
                credit.userId().value(),
                credit.balance(),
                credit.version(),
                Instant.now(),
            )
    }

    fun toDomain(): UserCredit =
        UserCredit(
            UserId.of(userId),
            balance,
            version,
        )
}
