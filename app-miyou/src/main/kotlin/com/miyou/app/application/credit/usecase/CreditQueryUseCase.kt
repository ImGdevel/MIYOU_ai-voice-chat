package com.miyou.app.application.credit.usecase

import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.UserCredit
import org.springframework.data.domain.Pageable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CreditQueryUseCase {
    fun getBalance(userId: String): Mono<UserCredit>

    fun getTransactions(
        userId: String,
        pageable: Pageable,
    ): Flux<CreditTransaction>
}
