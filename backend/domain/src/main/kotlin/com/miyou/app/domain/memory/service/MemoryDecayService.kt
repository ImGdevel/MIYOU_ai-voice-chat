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
     * 시간에 따른 실제 메모리 중요도의 감쇠를 수행하여 중요도가 갱신된 새로운 객체를 반환합니다.
     *
     * 예외 조건:
     * - 중요도가 핵심 중요도 임계치([decayExemptThreshold]) 이상이거나,
     * - 감정 상태가 충격적인 사건([MemoryEmotion.SHOCKING])인 경우 감쇠에서 면제됩니다.
     *
     * 감쇠 기준 시점은 마지막 접근 시점([Memory.lastAccessedAt]) 또는 생성 시점([Memory.createdAt]) 중 늦은 시점입니다.
     *
     * @param memory 감쇠 대상 메모리
     * @param now 현재 일시
     * @param decayRateHigh 중요도가 높을 때 적용할 빠른 감쇠율
     * @param decayRateLow 중요도가 낮을 때 적용할 느린 감쇠율
     * @param decayExemptThreshold 감쇠를 면제받는 핵심 기억 중요도 기준점
     * @return 중요도가 감쇠된 새로운 [Memory] 객체
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
     * 소프트 아카이브(보관 처리) 대상인지 여부를 판별합니다.
     *
     * 판별 규칙:
     * - 이미 아카이브 되었거나 감정 상태가 [MemoryEmotion.SHOCKING]인 기억은 대상에서 제외합니다.
     * - 중요도가 아카이브 임계치([archiveImportanceThreshold])보다 낮거나,
     * - 최종 접근 후 대기 일수([archiveIdleDays])를 초과한 경우 아카이브 대상으로 간주합니다.
     *
     * @param memory 판별 대상 메모리
     * @param now 현재 일시
     * @param archiveImportanceThreshold 아카이브를 수행할 중요도 기준점
     * @param archiveIdleDays 미접근 아카이브 대기 일수
     * @return 아카이브 대상 여부
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
