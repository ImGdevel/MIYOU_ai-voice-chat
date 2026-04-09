package com.miyou.app.infrastructure.credit.adapter

import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.domain.credit.port.UserCreditRepository
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.infrastructure.credit.document.UserCreditDocument
import com.miyou.app.infrastructure.credit.repository.UserCreditMongoRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class UserCreditMongoAdapter(
    private val mongoRepository: UserCreditMongoRepository,
) : UserCreditRepository {
    override fun findByUserId(userId: UserId): Mono<UserCredit> =
        mongoRepository.findByUserId(userId.value()).map(UserCreditDocument::toDomain)

    override fun save(userCredit: UserCredit): Mono<UserCredit> =
        mongoRepository
            .save(UserCreditDocument.fromDomain(userCredit))
            .map(UserCreditDocument::toDomain)
}
