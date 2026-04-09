package com.miyou.app.domain.credit.model

import com.miyou.app.domain.dialogue.model.ConversationSessionId

data class ConversationDeduction(
    val sessionId: ConversationSessionId,
) : CreditSource {
    override fun sourceType(): CreditSourceType = CreditSourceType.CONVERSATION_DEDUCTION

    fun sessionId(): ConversationSessionId = sessionId
}
