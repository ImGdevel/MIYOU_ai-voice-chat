package com.miyou.app.domain.memory.service

import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryEmotion
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.fixture.ConversationSessionFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class MemoryDecayServiceTest {
    private val sessionId = ConversationSessionFixture.createId().value
    private val service = MemoryDecayService()

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
        // given - 1년 전에 접근되었으나 중요도가 매우 높은(0.95f) 메모리 준비 (임계값 0.9f 이상)
        val memory = memoryWith(0.95f, Instant.now().minus(365, ChronoUnit.DAYS))

        // when - 감쇠 공식 적용
        val decayed =
            service.decayImportance(
                memory,
                Instant.now(),
                decayRateHigh = 0.05f,
                decayRateLow = 0.1f,
                decayExemptThreshold = 0.9f
            )

        // then - 감쇠 면제 대상이므로 원래 중요도 0.95f가 그대로 유지되는지 검증
        assertThat(decayed.importance).isEqualTo(0.95f)
    }

    @Test
    @DisplayName("importance가 0.5 이상 0.9 미만이면 저속 감쇠율을 적용한다")
    fun decayImportance_appliesLowRateForMidTier() {
        // given - 30일 전에 접근되었고 중요도가 중간 단계(0.7f)인 메모리 준비
        val now = Instant.now()
        val memory = memoryWith(0.7f, now.minus(30, ChronoUnit.DAYS))

        // when - 감쇠 공식 적용
        val decayed =
            service.decayImportance(
                memory,
                now,
                decayRateHigh = 0.05f,
                decayRateLow = 0.1f,
                decayExemptThreshold = 0.9f
            )

        // then - 중요도가 감쇠되었으며 하한선(0.1f)보다는 높고 초기값(0.7f)보다는 낮은지 검증
        assertThat(decayed.importance).isNotNull().isLessThan(0.7f)
        assertThat(decayed.importance).isGreaterThan(0.1f)
    }

    @Test
    @DisplayName("importance가 0.5 미만이면 일반 감쇠율을 적용해 더 빨리 깎인다")
    fun decayImportance_appliesHighRateForLowTier() {
        // given - 30일 전에 접근된 중요도 0.49f(낮음) 메모리와 중요도 0.7f(중간) 메모리 준비
        val now = Instant.now()
        val midTier = memoryWith(0.49f, now.minus(30, ChronoUnit.DAYS))
        val highTier = memoryWith(0.7f, now.minus(30, ChronoUnit.DAYS))

        // when - 두 메모리에 각각 감쇠 공식 적용
        val decayedMid =
            service.decayImportance(
                midTier,
                now,
                decayRateHigh = 0.05f,
                decayRateLow = 0.1f,
                decayExemptThreshold = 0.9f
            )
        val decayedHigh =
            service.decayImportance(
                highTier,
                now,
                decayRateHigh = 0.05f,
                decayRateLow = 0.1f,
                decayExemptThreshold = 0.9f
            )

        // then - 중요도가 낮을수록 감쇠 후 남은 비율이 더 적은지(즉, 더 빠르게 감쇠하는지) 비율을 비교 검증
        val midRatio = decayedMid.importance!! / midTier.importance!!
        val highRatio = decayedHigh.importance!! / highTier.importance!!
        assertThat(midRatio).isLessThan(highRatio)
    }

    @Test
    @DisplayName("importance가 archiveImportanceThreshold 미만이면 아카이브 대상이다")
    fun shouldArchive_trueWhenImportanceBelowThreshold() {
        // given - 아카이브 임계값(0.1f)보다 낮은 중요도(0.05f)를 지닌 메모리 준비
        val memory = memoryWith(0.05f, Instant.now())

        // when - 아카이브 대상 여부 검사
        val result =
            service.shouldArchive(
                memory,
                Instant.now(),
                archiveImportanceThreshold = 0.1f,
                archiveIdleDays = 90
            )

        // then - 아카이브 대상으로 마킹(true)되었는지 검증
        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("archiveIdleDays 이상 미접근이면 importance와 무관하게 아카이브 대상이다")
    fun shouldArchive_trueWhenIdleTooLong() {
        // given - 중요도는 높지만(0.8f) 방치된 지 90일이 지난 메모리 준비
        val now = Instant.now()
        val memory = memoryWith(0.8f, now.minus(91, ChronoUnit.DAYS))

        // when - 아카이브 대상 여부 검사
        val result = service.shouldArchive(memory, now, archiveImportanceThreshold = 0.1f, archiveIdleDays = 90)

        // then - 방치 기간 초과로 인해 아카이브 대상으로 마킹(true)되었는지 검증
        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("importance가 충분히 높고 최근에 접근했으면 아카이브 대상이 아니다")
    fun shouldArchive_falseWhenHealthy() {
        // given - 최근에 생성/접근되었고 중요도도 높은(0.8f) 건강한 메모리 준비
        val memory = memoryWith(0.8f, Instant.now())

        // when - 아카이브 대상 여부 검사
        val result =
            service.shouldArchive(
                memory,
                Instant.now(),
                archiveImportanceThreshold = 0.1f,
                archiveIdleDays = 90
            )

        // then - 아카이브 대상이 아님(false)을 검증
        assertThat(result).isFalse()
    }

    @Test
    @DisplayName("이미 아카이브된 메모리는 다시 아카이브 대상이 되지 않는다")
    fun shouldArchive_falseWhenAlreadyArchived() {
        // given - 이미 archivedAt 값이 설정된 아카이브 상태의 메모리 준비
        val now = Instant.now()
        val memory = memoryWith(0.01f, now.minus(200, ChronoUnit.DAYS), archivedAt = now)

        // when - 아카이브 대상 여부 검사
        val result = service.shouldArchive(memory, now, archiveImportanceThreshold = 0.1f, archiveIdleDays = 90)

        // then - 중복 아카이브 방지를 위해 false가 반환되는지 검증
        assertThat(result).isFalse()
    }

    @Test
    @DisplayName("archive()는 archivedAt을 설정한 복사본을 반환한다")
    fun archive_setsArchivedAt() {
        // given - 아카이브할 일반 메모리 설정
        val now = Instant.now()
        val memory = memoryWith(0.5f, now)

        // when - 아카이브 처리 실행
        val archived = service.archive(memory, now)

        // then - 반환된 복사본의 archivedAt이 현재 시각으로 설정되었는지 검증
        assertThat(archived.archivedAt).isEqualTo(now)
    }

    @Test
    @DisplayName("emotion이 SHOCKING이면 importance가 낮아도 감쇠하지 않는다")
    fun decayImportance_exemptsShockingEmotionRegardlessOfImportance() {
        // given - 중요도는 낮으나(0.2f) 감정이 SHOCKING으로 마킹된 충격적인 메모리 준비
        val now = Instant.now()
        val memory = memoryWith(0.2f, now.minus(200, ChronoUnit.DAYS), emotion = MemoryEmotion.SHOCKING)

        // when - 감쇠 공식 적용
        val decayed =
            service.decayImportance(
                memory,
                now,
                decayRateHigh = 0.05f,
                decayRateLow = 0.1f,
                decayExemptThreshold = 0.9f
            )

        // then - 감정 상태가 SHOCKING이므로 감쇠가 면제되어 기존 중요도(0.2f)가 그대로 유지되는지 검증
        assertThat(decayed.importance).isEqualTo(0.2f)
    }

    @Test
    @DisplayName("emotion이 SHOCKING이면 오래 미접근해도 아카이브 대상이 아니다")
    fun shouldArchive_falseWhenShockingRegardlessOfIdleTime() {
        // given - 200일 이상 방치되었고 중요도도 낮지만 감정이 SHOCKING인 메모리 준비
        val now = Instant.now()
        val memory = memoryWith(0.05f, now.minus(200, ChronoUnit.DAYS), emotion = MemoryEmotion.SHOCKING)

        // when - 아카이브 대상 여부 검사
        val result = service.shouldArchive(memory, now, archiveImportanceThreshold = 0.1f, archiveIdleDays = 90)

        // then - 충격적인 감정 메모리는 방치 시간과 무관하게 아카이브되지 않음(false)을 검증
        assertThat(result).isFalse()
    }
}
