package com.miyou.app.domain.memory.model

import com.miyou.app.fixture.ConversationSessionFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class MemoryTest {
    private val sessionId = ConversationSessionFixture.createId()

    private fun memoryWith(
        importance: Float,
        lastAccessedAt: Instant,
        archivedAt: Instant? = null,
        emotion: MemoryEmotion? = null,
    ): Memory =
        Memory(
            id = "mem-1",
            sessionId = sessionId,
            type = MemoryType.FACTUAL,
            content = "content",
            importance = importance,
            createdAt = lastAccessedAt,
            lastAccessedAt = lastAccessedAt,
            accessCount = 1,
            archivedAt = archivedAt,
            emotion = emotion,
        )

    @Test
    @DisplayName("importance가 감쇠 면제 임계값 이상이면 감쇠하지 않는다")
    fun decayImportance_exemptsHighImportance() {
        val memory = memoryWith(0.95f, Instant.now().minus(365, ChronoUnit.DAYS))

        val decayed =
            memory.decayImportance(
                Instant.now(),
                decayRateHigh = 0.05f,
                decayRateLow = 0.1f,
                decayExemptThreshold = 0.9f
            )

        assertThat(decayed.importance).isEqualTo(0.95f)
    }

    @Test
    @DisplayName("importance가 0.5 이상 0.9 미만이면 저속 감쇠율을 적용한다")
    fun decayImportance_appliesLowRateForMidTier() {
        val now = Instant.now()
        val memory = memoryWith(0.7f, now.minus(30, ChronoUnit.DAYS))

        val decayed =
            memory.decayImportance(
                now,
                decayRateHigh = 0.05f,
                decayRateLow = 0.1f,
                decayExemptThreshold = 0.9f
            )

        assertThat(decayed.importance).isNotNull().isLessThan(0.7f)
        assertThat(decayed.importance).isGreaterThan(0.1f)
    }

    @Test
    @DisplayName("importance가 0.5 미만이면 일반 감쇠율을 적용해 더 빨리 깎인다")
    fun decayImportance_appliesHighRateForLowTier() {
        val now = Instant.now()
        val midTier = memoryWith(0.49f, now.minus(30, ChronoUnit.DAYS))
        val highTier = memoryWith(0.7f, now.minus(30, ChronoUnit.DAYS))

        val decayedMid =
            midTier.decayImportance(
                now,
                decayRateHigh = 0.05f,
                decayRateLow = 0.1f,
                decayExemptThreshold = 0.9f
            )
        val decayedHigh =
            highTier.decayImportance(
                now,
                decayRateHigh = 0.05f,
                decayRateLow = 0.1f,
                decayExemptThreshold = 0.9f
            )

        val midRatio = decayedMid.importance!! / midTier.importance!!
        val highRatio = decayedHigh.importance!! / highTier.importance!!
        assertThat(midRatio).isLessThan(highRatio)
    }

    @Test
    @DisplayName("importance가 archiveImportanceThreshold 미만이면 아카이브 대상이다")
    fun shouldArchive_trueWhenImportanceBelowThreshold() {
        val memory = memoryWith(0.05f, Instant.now())

        val result = memory.shouldArchive(Instant.now(), archiveImportanceThreshold = 0.1f, archiveIdleDays = 90)

        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("archiveIdleDays 이상 미접근이면 importance와 무관하게 아카이브 대상이다")
    fun shouldArchive_trueWhenIdleTooLong() {
        val now = Instant.now()
        val memory = memoryWith(0.8f, now.minus(91, ChronoUnit.DAYS))

        val result = memory.shouldArchive(now, archiveImportanceThreshold = 0.1f, archiveIdleDays = 90)

        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("importance가 충분히 높고 최근에 접근했으면 아카이브 대상이 아니다")
    fun shouldArchive_falseWhenHealthy() {
        val memory = memoryWith(0.8f, Instant.now())

        val result = memory.shouldArchive(Instant.now(), archiveImportanceThreshold = 0.1f, archiveIdleDays = 90)

        assertThat(result).isFalse()
    }

    @Test
    @DisplayName("이미 아카이브된 메모리는 다시 아카이브 대상이 되지 않는다")
    fun shouldArchive_falseWhenAlreadyArchived() {
        val now = Instant.now()
        val memory = memoryWith(0.01f, now.minus(200, ChronoUnit.DAYS), archivedAt = now)

        val result = memory.shouldArchive(now, archiveImportanceThreshold = 0.1f, archiveIdleDays = 90)

        assertThat(result).isFalse()
    }

    @Test
    @DisplayName("archive()는 archivedAt을 설정한 복사본을 반환한다")
    fun archive_setsArchivedAt() {
        val now = Instant.now()
        val memory = memoryWith(0.5f, now)

        val archived = memory.archive(now)

        assertThat(archived.archivedAt).isEqualTo(now)
    }

    @Test
    @DisplayName("calculateRankedScore는 반복 조회로 lastAccessedAt이 갱신돼도 점수가 변하지 않는다")
    fun calculateRankedScore_ignoresLastAccessedAtRefresh() {
        val now = Instant.now()
        val staleContent =
            Memory(
                id = "mem-1",
                sessionId = sessionId,
                type = MemoryType.FACTUAL,
                content = "content",
                importance = 0.7f,
                createdAt = now.minus(30, ChronoUnit.DAYS),
                lastAccessedAt = now.minus(30, ChronoUnit.DAYS),
                accessCount = 1,
            )
        // 검색될 때마다 withAccess()가 lastAccessedAt만 최신화하는 상황을 재현 - createdAt은 그대로.
        val repeatedlyAccessed = staleContent.copy(lastAccessedAt = now)

        val scoreBeforeAccess = staleContent.calculateRankedScore(0.1f)
        val scoreAfterAccess = repeatedlyAccessed.calculateRankedScore(0.1f)

        assertThat(scoreAfterAccess).isEqualTo(scoreBeforeAccess)
    }

    @Test
    @DisplayName("calculateRankedScore는 lastAccessedAt과 무관하게 createdAt이 최근일수록 높은 점수를 준다")
    fun calculateRankedScore_prefersRecentlyCreatedMemory() {
        val now = Instant.now()
        val recent =
            Memory(
                id = "mem-recent",
                sessionId = sessionId,
                type = MemoryType.FACTUAL,
                content = "content",
                importance = 0.7f,
                createdAt = now,
                lastAccessedAt = now,
                accessCount = 1,
            )
        val old =
            Memory(
                id = "mem-old",
                sessionId = sessionId,
                type = MemoryType.FACTUAL,
                content = "content",
                importance = 0.7f,
                createdAt = now.minus(90, ChronoUnit.DAYS),
                lastAccessedAt = now,
                accessCount = 1,
            )

        assertThat(recent.calculateRankedScore(0.1f)).isGreaterThan(old.calculateRankedScore(0.1f))
    }

    @Test
    @DisplayName("emotion이 SHOCKING이면 importance가 낮아도 감쇠하지 않는다")
    fun decayImportance_exemptsShockingEmotionRegardlessOfImportance() {
        val now = Instant.now()
        val memory = memoryWith(0.2f, now.minus(200, ChronoUnit.DAYS), emotion = MemoryEmotion.SHOCKING)

        val decayed =
            memory.decayImportance(
                now,
                decayRateHigh = 0.05f,
                decayRateLow = 0.1f,
                decayExemptThreshold = 0.9f
            )

        assertThat(decayed.importance).isEqualTo(0.2f)
    }

    @Test
    @DisplayName("emotion이 SHOCKING이면 오래 미접근해도 아카이브 대상이 아니다")
    fun shouldArchive_falseWhenShockingRegardlessOfIdleTime() {
        val now = Instant.now()
        val memory = memoryWith(0.05f, now.minus(200, ChronoUnit.DAYS), emotion = MemoryEmotion.SHOCKING)

        val result = memory.shouldArchive(now, archiveImportanceThreshold = 0.1f, archiveIdleDays = 90)

        assertThat(result).isFalse()
    }
}
