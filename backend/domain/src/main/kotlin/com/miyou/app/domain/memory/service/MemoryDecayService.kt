package com.miyou.app.domain.memory.service

import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryEmotion
import java.time.Instant
import kotlin.math.exp

/**
 * 메모리 감쇠(decay) 및 아카이브(archive) 도메인 서비스.
 *
 * 시간 경과에 따른 메모리 중요도 감쇠와 소프트 아카이브 판별/처리를 담당한다.
 */
class MemoryDecayService {
    /**
     * 특정 조건(임계치 이상 중요도, 충격 사건 등)을 제외하고, 시간 경과에 따라 메모리 중요도를 감쇠시킵니다.
     */
    fun decayImportance(
        memory: Memory,
        now: Instant,
        decayRateHigh: Float,
        decayRateLow: Float,
        decayExemptThreshold: Float,
    ): Memory {
        val current = memory.importance ?: return memory
        if (current >= decayExemptThreshold || memory.emotion == MemoryEmotion.SHOCKING) return memory

        val lastAccess = memory.lastAccessedAt ?: memory.createdAt
        val hoursSinceAccess = maxOf(0.0, (now.epochSecond - lastAccess.epochSecond) / 3600.0)
        val rate = if (current >= MID_IMPORTANCE_THRESHOLD) decayRateHigh else decayRateLow
        val decayed = (current * exp(-rate * hoursSinceAccess / 24.0)).toFloat()
        return memory.copy(importance = decayed.coerceIn(0.0f, 1.0f))
    }

    /**
     * 중요도 미달 또는 미접근 일수 초과 여부를 판단하여 소프트 아카이브 대상인지 판별합니다.
     */
    fun shouldArchive(
        memory: Memory,
        now: Instant,
        archiveImportanceThreshold: Float,
        archiveIdleDays: Long,
    ): Boolean {
        if (memory.archivedAt != null || memory.emotion == MemoryEmotion.SHOCKING) return false
        val current = memory.importance ?: return false
        val lastAccess = memory.lastAccessedAt ?: memory.createdAt
        val idleDays = maxOf(0L, (now.epochSecond - lastAccess.epochSecond) / SECONDS_PER_DAY)
        return current < archiveImportanceThreshold || idleDays >= archiveIdleDays
    }

    /**
     * 메모리를 보관 처리(아카이브) 합니다.
     *
     * @param memory 아카이브 대상 메모리
     * @param now 아카이브 처리 일시
     * @return 아카이브 일시가 설정된 새로운 [Memory] 객체
     */
    fun archive(
        memory: Memory,
        now: Instant,
    ): Memory = memory.copy(archivedAt = now)

    companion object {
        private const val MID_IMPORTANCE_THRESHOLD = 0.5f
        private const val SECONDS_PER_DAY = 86400L
    }
}
