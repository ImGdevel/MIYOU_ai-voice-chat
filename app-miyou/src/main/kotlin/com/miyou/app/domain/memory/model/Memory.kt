package com.miyou.app.domain.memory.model

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import java.time.Instant
import kotlin.math.exp
import kotlin.math.min

data class Memory(
    val id: String?,
    val sessionId: ConversationSessionId,
    val type: MemoryType,
    val content: String,
    val importance: Float?,
    val createdAt: Instant,
    val lastAccessedAt: Instant?,
    val accessCount: Int?,
) {
    init {
        require(content.isNotBlank()) { "content cannot be null or blank" }
        require(type != null) { "type cannot be null" }
        require(importance == null || importance in 0.0f..1.0f) {
            "importance must be between 0.0 and 1.0"
        }
    }

    fun withId(newId: String?): Memory = copy(id = newId)

    fun withAccess(importanceBoost: Float): Memory {
        val currentImportance = importance ?: 0.0f
        val newImportance = min(1.0f, currentImportance + importanceBoost)
        val currentAccessCount = accessCount ?: 0
        return copy(
            importance = newImportance,
            lastAccessedAt = Instant.now(),
            accessCount = currentAccessCount + 1,
        )
    }

    fun calculateRankedScore(recencyWeight: Float): Float {
        val baseImportance = importance ?: 0.5f
        val lastAccessedAtValue = lastAccessedAt ?: return baseImportance
        val hoursSinceAccess = (Instant.now().epochSecond - lastAccessedAtValue.epochSecond) / 3600
        val recencyFactor = exp(-recencyWeight * hoursSinceAccess / 24.0).toFloat()
        return baseImportance * recencyFactor
    }

    companion object {
        fun create(
            sessionId: ConversationSessionId,
            type: MemoryType,
            content: String,
            importance: Float,
        ): Memory {
            val now = Instant.now()
            return Memory(null, sessionId, type, content, importance, now, now, 0)
        }
    }
}
