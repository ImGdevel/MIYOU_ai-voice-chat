package com.miyou.app.infrastructure.memory.adapter

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.ExtractedMemory
import com.miyou.app.domain.memory.model.MemoryType

data class MemoryExtractionDto(
    val type: String,
    val content: String,
    val importance: Float,
    val reasoning: String,
) {
    fun toExtractedMemory(sessionId: ConversationSessionId): ExtractedMemory =
        ExtractedMemory(sessionId, MemoryType.valueOf(type.uppercase()), content, importance, reasoning)
}
