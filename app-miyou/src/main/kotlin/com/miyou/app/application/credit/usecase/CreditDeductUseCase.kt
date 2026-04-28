package com.miyou.app.application.credit.usecase

import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.UserId
import reactor.core.publisher.Mono

interface CreditDeductUseCase {
    fun deductForConversation(
        userId: UserId,
        sessionId: ConversationSessionId,
    ): Mono<CreditTransaction>

    fun refundForConversation(
        userId: UserId,
        sessionId: ConversationSessionId,
    ): Mono<CreditTransaction>
}
