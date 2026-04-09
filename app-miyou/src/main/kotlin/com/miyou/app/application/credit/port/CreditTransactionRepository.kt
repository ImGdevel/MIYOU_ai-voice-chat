package com.miyou.app.application.credit.port

import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.dialogue.model.UserId
import org.springframework.data.domain.Pageable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CreditTransactionRepository {
    fun save(transaction: CreditTransaction): Mono<CreditTransaction>

    fun findByUserIdOrderByCreatedAtDesc(
        userId: UserId,
        pageable: Pageable,
    ): Flux<CreditTransaction>
}
