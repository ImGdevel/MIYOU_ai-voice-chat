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
     * 검색 랭킹용 점수(Recency 기반 중요도 감쇠)를 계산합니다.
     *
     * 주의: 이 계산은 검색 랭킹 평가용 임시 점수 계산이며, 실제 저장된 importance 값을 감쇠시키지 않습니다.
     * 자주 조회된다고 해서 오래된 기억이 최신처럼 랭킹되지 않도록, 접근 시점(lastAccessedAt)이 아닌
     * 실제 사건/생성 시점(createdAt) 기준으로 감쇠를 적용합니다.
     *
     * @param recencyWeight 최신성 가중치 (값이 클수록 시간이 지남에 따라 점수가 빠르게 감소)
     * @return 랭킹 계산용 점수
     */
    fun calculateRankedScore(recencyWeight: Float): Float {
        val baseImportance = importance ?: 0.5f
        val hoursSinceCreated = maxOf(0.0, (Instant.now().epochSecond - createdAt.epochSecond) / 3600.0)
        val recencyFactor = exp(-recencyWeight * hoursSinceCreated / 24.0).toFloat()
        return baseImportance * recencyFactor
    }

    /**
     * 시간에 따른 실제 메모리 중요도의 감쇠를 수행하여 중요도가 갱신된 새로운 객체를 반환합니다.
     *
     * 예외 조건:
     * - 중요도가 핵심 중요도 임계치([decayExemptThreshold]) 이상이거나,
     * - 감정 상태가 충격적인 사건([MemoryEmotion.SHOCKING])인 경우 감쇠에서 면제됩니다.
     *
     * 감쇠 기준 시점은 마지막 접근 시점([lastAccessedAt]) 또는 생성 시점([createdAt]) 중 늦은 시점입니다.
     *
     * @param now 현재 일시
     * @param decayRateHigh 중요도가 높을 때 적용할 빠른 감쇠율
     * @param decayRateLow 중요도가 낮을 때 적용할 느린 감쇠율
     * @param decayExemptThreshold 감쇠를 면제받는 핵심 기억 중요도 기준점
     * @return 중요도가 감쇠된 새로운 [Memory] 객체
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
     * 소프트 아카이브(보관 처리) 대상인지 여부를 판별합니다.
     *
     * 판별 규칙:
     * - 이미 아카이브 되었거나 감정 상태가 [MemoryEmotion.SHOCKING]인 기억은 대상에서 제외합니다.
     * - 중요도가 아카이브 임계치([archiveImportanceThreshold])보다 낮거나,
     * - 최종 접근 후 대기 일수([archiveIdleDays])를 초과한 경우 아카이브 대상으로 간주합니다.
     *
     * @param now 현재 일시
     * @param archiveImportanceThreshold 아카이브를 수행할 중요도 기준점
     * @param archiveIdleDays 미접근 아카이브 대기 일수
     * @return 아카이브 대상 여부
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

    /**
     * 메모리를 보관 처리(아카이브) 합니다.
     *
     * @param now 아카이브 처리 일시
     * @return 아카이브 일시가 설정된 새로운 [Memory] 객체
     */
    fun archive(now: Instant): Memory = copy(archivedAt = now)

    companion object {
        private const val MID_IMPORTANCE_THRESHOLD = 0.5f
        private const val SECONDS_PER_DAY = 86400L

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
