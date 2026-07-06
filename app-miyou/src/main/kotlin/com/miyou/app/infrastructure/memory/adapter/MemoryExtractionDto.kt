package com.miyou.app.infrastructure.memory.adapter

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.ExtractedMemory
import com.miyou.app.domain.memory.model.MemoryEmotion
import com.miyou.app.domain.memory.model.MemoryType
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

data class MemoryExtractionDto(
    val type: String,
    val content: String,
    val importance: Float,
    val reasoning: String,
    val supersedesMemoryId: String? = null,
    val emotion: String? = null,
) {
    fun toExtractedMemory(sessionId: ConversationSessionId): ExtractedMemory =
        ExtractedMemory(
            sessionId,
            MemoryType.valueOf(type.uppercase()),
            content,
            importance,
            reasoning,
            supersedesMemoryId,
            parseEmotion(),
        )

    private fun parseEmotion(): MemoryEmotion? {
        if (emotion.isNullOrBlank()) return null
        return try {
            MemoryEmotion.valueOf(emotion.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            log.warn { "알 수 없는 emotion 값 무시: $emotion" }
            null
        }
    }
}
