package com.miyou.app.infrastructure.memory.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.LlmPort
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryExtractionContext
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.support.anyValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class LlmMemoryExtractionAdapterTest {
    @org.mockito.Mock
    private lateinit var llmPort: LlmPort

    private lateinit var adapter: LlmMemoryExtractionAdapter

    @BeforeEach
    fun setUp() {
        adapter =
            LlmMemoryExtractionAdapter(
                llmPort,
                jacksonObjectMapper(),
                MemoryExtractionConfig("gpt-4o-mini", 5, 0.2f, 0.3f),
            )
    }

    @Test
    @DisplayName("유효한 JSON 응답을 메모리 객체로 변환한다")
    fun extractMemories_mapsValidJsonResponse() {
        val sessionId = ConversationSessionFixture.createId()
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(
                    ConversationTurn.withId(
                        "turn-1",
                        sessionId,
                        "I moved to Seoul",
                        "That sounds exciting.",
                        Instant.now(),
                    ),
                ),
                emptyList(),
            )
        val llmResponse =
            """[{"type":"FACTUAL","content":"The user lives in Seoul","importance":0.7,"reasoning":"Residence is stable profile information"}]"""

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just(llmResponse))

        StepVerifier
            .create(adapter.extractMemories(context))
            .assertNext { result ->
                assertThat(result.sessionId).isEqualTo(sessionId)
                assertThat(result.type).isEqualTo(MemoryType.FACTUAL)
                assertThat(result.content).isEqualTo("The user lives in Seoul")
                assertThat(result.importance).isEqualTo(0.7f)
            }.verifyComplete()

        verify(llmPort).complete(anyValue())
    }

    @Test
    @DisplayName("마크다운 코드 블록에 감싼 JSON도 파싱한다")
    fun extractMemories_parsesMarkdownJson() {
        val sessionId = ConversationSessionFixture.createId()
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(ConversationTurn.create(sessionId, "Remember that I like tea")),
                emptyList(),
            )
        val response =
            """
            ```json
            [{"type":"FACTUAL","content":"The user likes tea","importance":0.5,"reasoning":"Preference"}]
            ```
            """.trimIndent()

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just(response))

        StepVerifier
            .create(adapter.extractMemories(context))
            .assertNext { result ->
                assertThat(result.content).isEqualTo("The user likes tea")
            }.verifyComplete()
    }

    @Test
    @DisplayName("유효하지 않은 JSON이면 빈 결과를 반환한다")
    fun extractMemories_returnsEmptyFluxForInvalidJson() {
        val sessionId = ConversationSessionFixture.createId()
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(ConversationTurn.create(sessionId, "This is invalid")),
                emptyList(),
            )

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just("invalid json"))

        StepVerifier.create(adapter.extractMemories(context)).verifyComplete()
    }

    @Test
    @DisplayName("여러 개의 추출 메모리를 모두 처리한다")
    fun extractMemories_handlesMultipleExtractedMemories() {
        val sessionId = ConversationSessionFixture.createId()
        val existingMemory =
            Memory(
                id = "mem-1",
                sessionId = sessionId,
                type = MemoryType.FACTUAL,
                content = "The user drinks coffee",
                importance = 0.7f,
                createdAt = Instant.now(),
                lastAccessedAt = Instant.now(),
                accessCount = 1,
            )
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(ConversationTurn.create(sessionId, "I also enjoy long walks")),
                listOf(existingMemory),
            )
        val response =
            """
            [
              {"type":"FACTUAL","content":"The user enjoys long walks","importance":0.6,"reasoning":"Preference"},
              {"type":"EXPERIENTIAL","content":"The user recently went walking","importance":0.8,"reasoning":"Recent experience"}
            ]
            """.trimIndent()

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just(response))

        StepVerifier
            .create(adapter.extractMemories(context))
            .assertNext { result ->
                assertThat(result.type).isEqualTo(MemoryType.FACTUAL)
            }.assertNext { result ->
                assertThat(result.type).isEqualTo(MemoryType.EXPERIENTIAL)
            }.verifyComplete()
    }
}
