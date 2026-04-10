package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.dialogue.model.Message
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import org.springframework.stereotype.Service

@Service
class DialogueMessageService(
    private val systemPromptService: SystemPromptService,
) {
    companion object {
        private const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant."
    }

    fun buildMessages(
        personaId: PersonaId,
        context: RetrievalContext,
        memories: MemoryRetrievalResult,
        conversationContext: ConversationContext,
        currentQuery: String,
    ): List<Message> {
        require(currentQuery.isNotBlank()) { "Current query must not be null or blank." }

        val messages =
            buildList {
                val fullSystemPrompt = systemPromptService.buildSystemPrompt(personaId, context, memories)
                add(Message.system(fullSystemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT }))

                conversationContext.turns.forEach { turn ->
                    if (turn.response != null) {
                        add(Message.user(turn.query))
                        add(Message.assistant(turn.response))
                    }
                }

                add(Message.user(currentQuery))
            }
        return messages
    }
}
