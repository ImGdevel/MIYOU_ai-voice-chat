package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.retrieval.model.RetrievalContext

interface PromptTemplatePort {
    fun buildPrompt(context: RetrievalContext): String

    fun buildPromptWithConversation(
        context: RetrievalContext,
        conversationContext: ConversationContext,
    ): String

    fun buildDefaultPrompt(): String
}
