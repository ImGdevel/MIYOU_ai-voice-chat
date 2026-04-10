package com.miyou.app.infrastructure.credit.adapter

import com.miyou.app.application.credit.port.CreditTransactionRepository
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.infrastructure.credit.document.CreditTransactionDocument
import com.miyou.app.infrastructure.credit.repository.CreditTransactionMongoRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class CreditTransactionMongoAdapter(
    private val mongoRepository: CreditTransactionMongoRepository,
) : CreditTransactionRepository {
    override fun save(transaction: CreditTransaction): Mono<CreditTransaction> =
        mongoRepository
            .save(CreditTransactionDocument.fromDomain(transaction))
            .map(CreditTransactionDocument::toDomain)

    override fun findByUserIdOrderByCreatedAtDesc(
        userId: UserId,
        pageable: Pageable,
    ): Flux<CreditTransaction> =
        mongoRepository
            .findByUserIdOrderByCreatedAtDesc(userId.value(), pageable)
            .map(CreditTransactionDocument::toDomain)
}
