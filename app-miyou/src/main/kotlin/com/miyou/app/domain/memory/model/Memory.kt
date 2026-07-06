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
     * 검색 랭킹용 recency는 접근 시점(lastAccessedAt)이 아니라 사건/생성 시점(createdAt)
     * 기준이다 - 자주 조회된다고 해서 오래된 기억이 최신처럼 랭킹되면 안 되기 때문.
     * (정리/아카이브 목적의 decayImportance/shouldArchive는 접근 시점 기준을 그대로 쓴다.)
     */
    fun calculateRankedScore(recencyWeight: Float): Float {
        val baseImportance = importance ?: 0.5f
        val hoursSinceCreated = maxOf(0.0, (Instant.now().epochSecond - createdAt.epochSecond) / 3600.0)
        val recencyFactor = exp(-recencyWeight * hoursSinceCreated / 24.0).toFloat()
        return baseImportance * recencyFactor
    }

    /**
     * 저장된 importance를 실제로 깎는다 (calculateRankedScore와 달리 랭킹용 계산이 아니라 저장값 자체를 갱신).
     * importance >= decayExemptThreshold(생일 등 핵심 기억) 또는 emotion == SHOCKING
     * (트라우마급 사건)은 감쇠 면제.
     */
    fun decayImportance(
        now: Instant,
        decayRateHigh: Float,
        decayRateLow: Float,
        decayExemptThreshold: Float,
    ): Memory {
        val current = importance ?: return this
        if (current >= decayExemptThreshold || emotion == MemoryEmotion.SHOCKING) return this

        val lastAccess = lastAccessedAt ?: createdAt
        val hoursSinceAccess = maxOf(0.0, (now.epochSecond - lastAccess.epochSecond) / 3600.0)
        val rate = if (current >= MID_IMPORTANCE_THRESHOLD) decayRateHigh else decayRateLow
        val decayed = (current * exp(-rate * hoursSinceAccess / 24.0)).toFloat()
        return copy(importance = decayed.coerceIn(0.0f, 1.0f))
    }

    /**
     * 소프트 아카이브 대상 여부. 이미 아카이브된 메모리는 대상에서 제외한다.
     * emotion == SHOCKING이면 미접근 기간과 무관하게 아카이브하지 않는다 - "자주 안
     * 물어봐도 잊으면 안 되는" 기억이 단지 오래 방치됐다는 이유로 정리되면 안 되기 때문.
     */
    fun shouldArchive(
        now: Instant,
        archiveImportanceThreshold: Float,
        archiveIdleDays: Long,
    ): Boolean {
        if (archivedAt != null || emotion == MemoryEmotion.SHOCKING) return false
        val current = importance ?: return false
        val lastAccess = lastAccessedAt ?: createdAt
        val idleDays = maxOf(0L, (now.epochSecond - lastAccess.epochSecond) / SECONDS_PER_DAY)
        return current < archiveImportanceThreshold || idleDays >= archiveIdleDays
    }

    fun archive(now: Instant): Memory = copy(archivedAt = now)

    companion object {
        private const val MID_IMPORTANCE_THRESHOLD = 0.5f
        private const val SECONDS_PER_DAY = 86400L

        fun create(
            sessionId: ConversationSessionId,
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
