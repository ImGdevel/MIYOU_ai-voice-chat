package com.miyou.app.domain.memory.model

import com.miyou.app.domain.dialogue.model.ConversationSessionId

data class ExtractedMemory(
    val sessionId: ConversationSessionId,
    val type: MemoryType,
    val content: String,
    val importance: Float,
    val reasoning: String,
) {
    init {
        require(content.isNotBlank()) { "content cannot be null or blank" }
        require(importance in 0.0f..1.0f) { "importance must be between 0.0 and 1.0" }
    }

    fun toMemory(): Memory = Memory.create(sessionId, type, content, importance)
}
