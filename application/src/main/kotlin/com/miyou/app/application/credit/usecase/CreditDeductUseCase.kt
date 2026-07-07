package com.miyou.app.application.credit.usecase

import com.miyou.app.domain.credit.model.CreditTransaction
import reactor.core.publisher.Mono

interface CreditDeductUseCase {
    fun deductForConversation(
        userId: String,
        sessionId: String,
    ): Mono<CreditTransaction>

    fun refundForConversation(
        userId: String,
        sessionId: String,
    ): Mono<CreditTransaction>
}
