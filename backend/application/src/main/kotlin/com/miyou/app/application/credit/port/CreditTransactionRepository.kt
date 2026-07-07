package com.miyou.app.application.credit.port

import com.miyou.app.domain.credit.model.CreditTransaction
import org.springframework.data.domain.Pageable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CreditTransactionRepository {
    fun save(transaction: CreditTransaction): Mono<CreditTransaction>

    fun findByUserIdOrderByCreatedAtDesc(
        userId: String,
        pageable: Pageable,
    ): Flux<CreditTransaction>
}
