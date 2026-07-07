package com.miyou.app.infrastructure.common.template

import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.dialogue.model.PromptRetrievalInput
import com.miyou.app.domain.dialogue.port.PromptTemplatePort
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component
class FileBasedPromptTemplateAdapter(
    private val templateLoader: FileBasedPromptTemplate,
) : PromptTemplatePort {
    override fun buildPrompt(input: PromptRetrievalInput): String {
        val contextText =
            if (input.retrievedTexts.isEmpty()) {
                ""
            } else {
                input.retrievedTexts.joinToString("\n")
            }
        return templateLoader.load(
            CONVERSATION_TEMPLATE,
            mapOf("context" to contextText, "conversation" to ""),
        )
    }

    override fun buildPromptWithConversation(
        input: PromptRetrievalInput,
        conversationContext: ConversationContext,
    ): String {
        val contextText =
            if (input.retrievedTexts.isEmpty()) {
                ""
            } else {
                input.retrievedTexts.joinToString("\n")
            }
        val conversationHistory = buildConversationHistory(conversationContext)
        return templateLoader.load(
            CONVERSATION_TEMPLATE,
            mapOf("context" to contextText, "conversation" to conversationHistory),
        )
    }

    override fun buildDefaultPrompt(): String =
        templateLoader.load(CONVERSATION_TEMPLATE, mapOf("context" to "", "conversation" to ""))

    private fun buildConversationHistory(conversationContext: ConversationContext): String {
        if (conversationContext.isEmpty()) {
            return ""
        }
        return conversationContext
            .turns()
            .filter { it.response != null }
            .joinToString("\n\n") { turn ->
                "User: ${turn.query}\nAssistant: ${turn.response}"
            }
    }

    companion object {
        private const val CONVERSATION_TEMPLATE = "dialogue/conversation"
    }
}
