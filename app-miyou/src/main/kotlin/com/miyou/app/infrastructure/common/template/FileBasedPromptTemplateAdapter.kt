package com.miyou.app.infrastructure.common.template

import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.dialogue.port.PromptTemplatePort
import com.miyou.app.domain.retrieval.model.RetrievalContext
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component
class FileBasedPromptTemplateAdapter(
    private val templateLoader: FileBasedPromptTemplate,
) : PromptTemplatePort {
    override fun buildPrompt(context: RetrievalContext): String {
        val contextText =
            if (context.isEmpty()) {
                ""
            } else {
                context.documents().joinToString("\n") { it.content }
            }
        return templateLoader.load(
            CONVERSATION_TEMPLATE,
            mapOf("context" to contextText, "conversation" to ""),
        )
    }

    override fun buildPromptWithConversation(
        context: RetrievalContext,
        conversationContext: ConversationContext,
    ): String {
        val contextText =
            if (context.isEmpty()) {
                ""
            } else {
                context.documents().joinToString("\n") { it.content }
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
