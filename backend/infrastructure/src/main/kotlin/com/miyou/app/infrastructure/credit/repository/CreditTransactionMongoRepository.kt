package com.miyou.app.infrastructure.credit.repository

import com.miyou.app.infrastructure.credit.document.CreditTransactionDocument
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux

interface CreditTransactionMongoRepository : ReactiveMongoRepository<CreditTransactionDocument, String> {
    fun findByUserIdOrderByCreatedAtDesc(
        userId: String,
        pageable: Pageable,
    ): Flux<CreditTransactionDocument>
}
