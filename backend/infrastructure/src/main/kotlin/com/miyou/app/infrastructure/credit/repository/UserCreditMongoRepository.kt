package com.miyou.app.infrastructure.credit.repository

import com.miyou.app.infrastructure.credit.document.UserCreditDocument
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface UserCreditMongoRepository : ReactiveMongoRepository<UserCreditDocument, String> {
    fun findByUserId(userId: String): Mono<UserCreditDocument>
}
