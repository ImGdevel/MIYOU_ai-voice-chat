package com.miyou.app.application.memory.service

import com.miyou.app.application.memory.policy.MemoryCuratorPolicy
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.VectorMemoryPort
import com.miyou.app.domain.memory.service.MemoryDecayService
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.support.anyValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(MockitoExtension::class)
class MemoryCuratorServiceTest {
    @Mock
    private lateinit var vectorMemoryPort: VectorMemoryPort

    private lateinit var service: MemoryCuratorService

    private val policy = MemoryCuratorPolicy(0.05f, 0.1f, 0.9f, 0.1f, 90L)

    @BeforeEach
    fun setUp() {
        service = MemoryCuratorService(vectorMemoryPort, policy, MemoryDecayService())
    }

    @Test
    @DisplayName("오래 미접근한 저importance 메모리는 감쇠 후 아카이브 마킹되어 저장된다")
    fun runDecayAndArchive_archivesStaleLowImportanceMemory() {
        // given - 감쇠 대상이 될 오래된(200일 전 생성 및 접근) 중요도 낮은 메모리 준비
        val sessionId = ConversationSessionFixture.createId().value
        val now = Instant.now()
        val stale =
            Memory(
                id = "mem-1",
                sessionId = sessionId,
                type = MemoryType.FACTUAL,
                content = "content",
                importance = 0.05f,
                createdAt = now.minus(200, ChronoUnit.DAYS),
                lastAccessedAt = now.minus(200, ChronoUnit.DAYS),
                accessCount = 1,
            )

        var captured: Memory? = null
        // vectorMemoryPort의 active 메모리 조회 모킹
        `when`(vectorMemoryPort.findAllActive(500)).thenReturn(Flux.just(stale))
        // 감쇠 및 아카이브 적용 시 캡처 로직 모킹
        `when`(vectorMemoryPort.applyDecayAndArchive(anyValue())).thenAnswer { invocation ->
            captured = invocation.getArgument(0)
            Mono.empty<Void>()
        }

        // when - 감쇠 및 아카이브 배치 작업 실행
        StepVerifier.create(service.runDecayAndArchive()).verifyComplete()

        // then - 중요도가 낮고 미사용 기간이 오래되어 아카이브 시각이 채워졌는지 검증
        assertThat(captured?.archivedAt).isNotNull()
    }

    @Test
    @DisplayName("건강한 메모리는 감쇠만 반영되고 아카이브되지 않는다")
    fun runDecayAndArchive_decaysHealthyMemoryWithoutArchiving() {
        // given - 최근에 생성된 건강하고 중요도 높은 메모리 준비
        val sessionId = ConversationSessionFixture.createId().value
        val now = Instant.now()
        val healthy =
            Memory(
                id = "mem-2",
                sessionId = sessionId,
                type = MemoryType.EXPERIENTIAL,
                content = "content",
                importance = 0.8f,
                createdAt = now,
                lastAccessedAt = now,
                accessCount = 1,
            )

        var captured: Memory? = null
        // active 메모리 조회 모킹
        `when`(vectorMemoryPort.findAllActive(500)).thenReturn(Flux.just(healthy))
        // 감쇠 및 아카이브 적용 시 캡처 로직 모킹
        `when`(vectorMemoryPort.applyDecayAndArchive(anyValue())).thenAnswer { invocation ->
            captured = invocation.getArgument(0)
            Mono.empty<Void>()
        }

        // when - 감쇠 및 아카이브 배치 작업 실행
        StepVerifier.create(service.runDecayAndArchive()).verifyComplete()

        // then - 건강한 메모리는 아카이브되지 않고(archivedAt이 null), 중요도가 유지(감쇠 미미)되는지 검증
        assertThat(captured?.archivedAt).isNull()
        assertThat(captured?.importance).isCloseTo(0.8f, Offset.offset(0.001f))
    }
}
