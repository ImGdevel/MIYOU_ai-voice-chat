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
        // given - 30일 전 생성 및 접근된 메모리와, 접근 시각(lastAccessedAt)만 현재로 갱신된 메모리 복사본 준비
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

        // when - 각각의 상태에서 랭킹 점수 계산 실행
        val scoreBeforeAccess = staleContent.calculateRankedScore(0.1f)
        val scoreAfterAccess = repeatedlyAccessed.calculateRankedScore(0.1f)

        // then - lastAccessedAt 갱신 여부와 상관없이 랭킹 점수가 동일한지 검증 (시간 감쇠는 오직 createdAt 기준)
        assertThat(scoreAfterAccess).isEqualTo(scoreBeforeAccess)
    }

    @Test
    @DisplayName("calculateRankedScore는 lastAccessedAt과 무관하게 createdAt이 최근일수록 높은 점수를 준다")
    fun calculateRankedScore_prefersRecentlyCreatedMemory() {
        // given - 최근 생성된 메모리와 90일 전 생성된 메모리(두 메모리 모두 접근 시각은 동일하게 설정) 준비
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

        // when & then - 최근 생성된 메모리의 랭킹 점수가 오래된 메모리의 랭킹 점수보다 높은지 검증
        assertThat(recent.calculateRankedScore(0.1f)).isGreaterThan(old.calculateRankedScore(0.1f))
    }
}
