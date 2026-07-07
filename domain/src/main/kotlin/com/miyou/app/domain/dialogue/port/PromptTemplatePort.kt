package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.dialogue.model.PromptRetrievalInput

interface PromptTemplatePort {
    fun buildPrompt(input: PromptRetrievalInput): String

    fun buildPromptWithConversation(
        input: PromptRetrievalInput,
        conversationContext: ConversationContext,
    ): String

    fun buildDefaultPrompt(): String
}
