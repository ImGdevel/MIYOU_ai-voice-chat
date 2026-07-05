package com.miyou.app.application.memory.service

import com.miyou.app.application.memory.policy.MemoryCuratorPolicy
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.VectorMemoryPort
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
        service = MemoryCuratorService(vectorMemoryPort, policy)
    }

    @Test
    @DisplayName("오래 미접근한 저importance 메모리는 감쇠 후 아카이브 마킹되어 저장된다")
    fun runDecayAndArchive_archivesStaleLowImportanceMemory() {
        val sessionId = ConversationSessionFixture.createId()
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
        `when`(vectorMemoryPort.findAllActive(500)).thenReturn(Flux.just(stale))
        `when`(vectorMemoryPort.applyDecayAndArchive(anyValue())).thenAnswer { invocation ->
            captured = invocation.getArgument(0)
            Mono.empty<Void>()
        }

        StepVerifier.create(service.runDecayAndArchive()).verifyComplete()

        assertThat(captured?.archivedAt).isNotNull()
    }

    @Test
    @DisplayName("건강한 메모리는 감쇠만 반영되고 아카이브되지 않는다")
    fun runDecayAndArchive_decaysHealthyMemoryWithoutArchiving() {
        val sessionId = ConversationSessionFixture.createId()
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
        `when`(vectorMemoryPort.findAllActive(500)).thenReturn(Flux.just(healthy))
        `when`(vectorMemoryPort.applyDecayAndArchive(anyValue())).thenAnswer { invocation ->
            captured = invocation.getArgument(0)
            Mono.empty<Void>()
        }

        StepVerifier.create(service.runDecayAndArchive()).verifyComplete()

        assertThat(captured?.archivedAt).isNull()
        assertThat(captured?.importance).isCloseTo(0.8f, Offset.offset(0.001f))
    }
}
