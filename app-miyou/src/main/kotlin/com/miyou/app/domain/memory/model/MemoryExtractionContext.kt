package com.miyou.app.domain.memory.model

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.ConversationTurn

data class MemoryExtractionContext(
    val sessionId: ConversationSessionId,
    val recentConversations: List<ConversationTurn> = emptyList(),
    val existingMemories: List<Memory> = emptyList(),
) {
    companion object {
        @JvmStatic
        fun of(
            sessionId: ConversationSessionId,
            conversations: List<ConversationTurn>,
            memories: List<Memory>,
        ): MemoryExtractionContext = MemoryExtractionContext(sessionId, conversations, memories)
    }
}
