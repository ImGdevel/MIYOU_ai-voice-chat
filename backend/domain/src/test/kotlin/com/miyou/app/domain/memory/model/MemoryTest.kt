package com.miyou.app.domain.memory.model

import com.miyou.app.fixture.ConversationSessionFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class MemoryTest {
    private val sessionId = ConversationSessionFixture.createId().value

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
}
