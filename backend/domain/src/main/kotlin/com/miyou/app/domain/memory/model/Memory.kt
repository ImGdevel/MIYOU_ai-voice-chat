package com.miyou.app.domain.memory.model

import java.time.Instant
import kotlin.math.exp
import kotlin.math.min

data class Memory(
    val id: String?,
    val sessionId: String,
    val type: MemoryType,
    val content: String,
    val importance: Float?,
    val createdAt: Instant,
    val lastAccessedAt: Instant?,
    val accessCount: Int?,
    val archivedAt: Instant? = null,
    val emotion: MemoryEmotion? = null,
) {
    init {
        require(content.isNotBlank()) { "content cannot be null or blank" }
        require(type != null) { "type cannot be null" }
        require(importance == null || importance in 0.0f..1.0f) {
            "importance must be between 0.0 and 1.0"
        }
    }

    fun withId(newId: String?): Memory = copy(id = newId)

    /**
     * 메모리에 접근했을 때의 처리.
     * 중요도를 일정 비율 가중하고, 접근 일시와 횟수를 업데이트합니다.
     *
     * @param importanceBoost 추가할 중요도 가중치
     * @return 갱신된 [Memory] 객체
     */
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

    /**
     * 생성 시점(createdAt) 기준으로 최신성이 감쇠된 검색 랭킹용 점수를 계산합니다.
     */
    fun calculateRankedScore(recencyWeight: Float): Float {
        val baseImportance = importance ?: 0.5f
        val hoursSinceCreated = maxOf(0.0, (Instant.now().epochSecond - createdAt.epochSecond) / 3600.0)
        val recencyFactor = exp(-recencyWeight * hoursSinceCreated / 24.0).toFloat()
        return baseImportance * recencyFactor
    }

    companion object {
        fun create(
            sessionId: String,
            type: MemoryType,
            content: String,
            importance: Float,
            emotion: MemoryEmotion? = null,
        ): Memory {
            val now = Instant.now()
            return Memory(null, sessionId, type, content, importance, now, now, 0, emotion = emotion)
        }
    }
}
