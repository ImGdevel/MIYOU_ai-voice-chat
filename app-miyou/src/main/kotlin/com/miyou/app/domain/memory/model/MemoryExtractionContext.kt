package com.miyou.app.domain.memory.model

data class ConversationSnippet(
    val query: String,
    val response: String?,
)

data class MemoryExtractionContext(
    val sessionId: String,
    val recentConversations: List<ConversationSnippet> = emptyList(),
    val existingMemories: List<Memory> = emptyList(),
) {
    companion object {
        @JvmStatic
        fun of(
            sessionId: String,
            conversations: List<ConversationSnippet>,
            memories: List<Memory>,
        ): MemoryExtractionContext = MemoryExtractionContext(sessionId, conversations, memories)
    }
}
