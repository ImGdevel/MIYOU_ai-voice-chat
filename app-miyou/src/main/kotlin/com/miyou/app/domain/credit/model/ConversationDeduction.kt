package com.miyou.app.domain.credit.model

data class ConversationDeduction(
    val sessionId: String,
) : CreditSource {
    override fun sourceType(): CreditSourceType = CreditSourceType.CONVERSATION_DEDUCTION

    fun sessionId(): String = sessionId
}
