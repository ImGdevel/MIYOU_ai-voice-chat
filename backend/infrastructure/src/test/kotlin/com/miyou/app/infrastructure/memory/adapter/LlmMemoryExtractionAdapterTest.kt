package com.miyou.app.infrastructure.memory.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.miyou.app.domain.dialogue.port.LlmPort
import com.miyou.app.domain.dialogue.port.TemplateLoaderPort
import com.miyou.app.domain.memory.model.ConversationSnippet
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryEmotion
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
                object : TemplateLoaderPort {
                    override fun load(templateName: String): String = "test system prompt"
                },
                MemoryExtractionConfig("gpt-4o-mini", 5, 0.2f, 0.3f),
            )
    }

    @Test
    @DisplayName("유효한 JSON 응답을 메모리 객체로 변환한다")
    fun extractMemories_mapsValidJsonResponse() {
        // given - 서울로 이사했다는 대화 내용 컨텍스트와 LLM의 정상 응답 JSON 모킹 설정
        val sessionIdObj = ConversationSessionFixture.createId()
        val sessionId = sessionIdObj.value
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(ConversationSnippet("I moved to Seoul", "That sounds exciting.")),
                emptyList(),
            )
        val llmResponse =
            """[{"type":"FACTUAL","content":"The user lives in Seoul","importance":0.7,"reasoning":"Residence is stable profile information"}]"""

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just(llmResponse))

        // when & then - JSON 응답이 올바르게 ExtractedMemory 객체 필드로 파싱 및 매핑되었는지 검증
        StepVerifier
            .create(adapter.extractMemories(context))
            .assertNext { result ->
                assertThat(result.sessionId).isEqualTo(sessionId)
                assertThat(result.type).isEqualTo(MemoryType.FACTUAL)
                assertThat(result.content).isEqualTo("The user lives in Seoul")
                assertThat(result.importance).isEqualTo(0.7f)
                assertThat(result.reasoning).isEqualTo("Residence is stable profile information")
            }.verifyComplete()

        verify(llmPort).complete(anyValue())
    }

    @Test
    @DisplayName("마크다운 코드 블록에 감싼 JSON도 파싱한다")
    fun extractMemories_parsesMarkdownJson() {
        // given - 마크다운 백틱(```json ... ```)으로 래핑된 LLM의 응답 모킹 설정
        val sessionIdObj = ConversationSessionFixture.createId()
        val sessionId = sessionIdObj.value
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(ConversationSnippet("Remember that I like tea", null)),
                emptyList(),
            )
        val response =
            """
            ```json
            [{"type":"FACTUAL","content":"The user likes tea","importance":0.5,"reasoning":"Preference"}]
            ```
            """.trimIndent()

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just(response))

        // when & then - 마크다운 문법이 제거되고 내부 JSON이 올바르게 파싱되는지 검증
        StepVerifier
            .create(adapter.extractMemories(context))
            .assertNext { result ->
                assertThat(result.content).isEqualTo("The user likes tea")
            }.verifyComplete()
    }

    @Test
    @DisplayName("유효하지 않은 JSON이면 빈 결과를 반환한다")
    fun extractMemories_returnsEmptyFluxForInvalidJson() {
        // given - 올바르지 않은 일반 텍스트 응답 모킹 설정
        val sessionIdObj = ConversationSessionFixture.createId()
        val sessionId = sessionIdObj.value
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(ConversationSnippet("This is invalid", null)),
                emptyList(),
            )

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just("invalid json"))

        // when & then - 파싱 실패 시 예외가 터지지 않고 부드럽게 빈 Flux를 반환하는지 검증
        StepVerifier.create(adapter.extractMemories(context)).verifyComplete()
    }

    @Test
    @DisplayName("여러 개의 추출 메모리를 모두 처리한다")
    fun extractMemories_handlesMultipleExtractedMemories() {
        // given - 기존 메모리 및 복수 개 배열 형태의 LLM JSON 응답 모킹 설정
        val sessionIdObj = ConversationSessionFixture.createId()
        val sessionId = sessionIdObj.value
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
                listOf(ConversationSnippet("I also enjoy long walks", null)),
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

        // when & then - 여러 개의 JSON 요소가 순차적으로 올바른 타입으로 파싱되어 방출되는지 검증
        StepVerifier
            .create(adapter.extractMemories(context))
            .assertNext { result ->
                assertThat(result.type).isEqualTo(MemoryType.FACTUAL)
            }.assertNext { result ->
                assertThat(result.type).isEqualTo(MemoryType.EXPERIENTIAL)
            }.verifyComplete()
    }

    @Test
    @DisplayName("컨텍스트에 존재하는 id를 가리키면 supersedesMemoryId를 그대로 받아들인다")
    fun extractMemories_acceptsSupersedesMemoryIdWhenTargetExistsInContext() {
        // given - 컨텍스트에 존재하는 기존 메모리 "mem-1"과 이를 덮어쓰도록(supersedesMemoryId="mem-1") 지정된 LLM 응답 설정
        val sessionIdObj = ConversationSessionFixture.createId()
        val sessionId = sessionIdObj.value
        val existingMemory =
            Memory(
                id = "mem-1",
                sessionId = sessionId,
                type = MemoryType.FACTUAL,
                content = "사용자는 라면을 좋아한다",
                importance = 0.6f,
                createdAt = Instant.now(),
                lastAccessedAt = Instant.now(),
                accessCount = 1,
            )
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(ConversationSnippet("이제 라면 안 먹어, 질려서", null)),
                listOf(existingMemory),
            )
        val response =
            """[{"type":"FACTUAL","content":"사용자는 라면을 싫어한다","importance":0.6,"reasoning":"선호도 변경","supersedesMemoryId":"mem-1"}]"""

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just(response))

        // when & then - 덮어쓸 대상 메모리가 컨텍스트에 존재하므로 supersedesMemoryId 값이 "mem-1"로 올바르게 채워졌는지 검증
        StepVerifier
            .create(adapter.extractMemories(context))
            .assertNext { result -> assertThat(result.supersedesMemoryId).isEqualTo("mem-1") }
            .verifyComplete()
    }

    @Test
    @DisplayName("컨텍스트에 없는 id를 가리키면 supersedesMemoryId를 무시한다")
    fun extractMemories_dropsSupersedesMemoryIdWhenTargetMissingFromContext() {
        // given - 빈 메모리 컨텍스트(기존 메모리가 없음)와 존재하지 않는 ID("mem-does-not-exist")를 덮어쓰려 하는 LLM 응답 설정
        val sessionIdObj = ConversationSessionFixture.createId()
        val sessionId = sessionIdObj.value
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(ConversationSnippet("이제 라면 안 먹어, 질려서", null)),
                emptyList(),
            )
        val response =
            """[{"type":"FACTUAL","content":"사용자는 라면을 싫어한다","importance":0.6,"reasoning":"선호도 변경","supersedesMemoryId":"mem-does-not-exist"}]"""

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just(response))

        // when & then - 덮어쓸 메모리가 실제 존재하지 않으므로 supersedesMemoryId 필드가 무시되어 null 처리되는지 검증
        StepVerifier
            .create(adapter.extractMemories(context))
            .assertNext { result -> assertThat(result.supersedesMemoryId).isNull() }
            .verifyComplete()
    }

    @Test
    @DisplayName("emotion 필드를 MemoryEmotion으로 파싱한다")
    fun extractMemories_parsesEmotionField() {
        // given - 감정이 SHOCKING으로 마킹된 슬픈 사건 대화 컨텍스트와 이에 맞는 LLM 응답 모킹 설정
        val sessionIdObj = ConversationSessionFixture.createId()
        val sessionId = sessionIdObj.value
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(ConversationSnippet("어제 반려동물을 잃었어", null)),
                emptyList(),
            )
        val response =
            """[{"type":"EXPERIENTIAL","content":"사용자가 반려동물을 잃었다","importance":0.9,"reasoning":"충격적 상실","emotion":"SHOCKING"}]"""

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just(response))

        // when & then - emotion 텍스트 필드가 도메인 Enum인 MemoryEmotion.SHOCKING으로 정확히 매핑되는지 검증
        StepVerifier
            .create(adapter.extractMemories(context))
            .assertNext { result -> assertThat(result.emotion).isEqualTo(MemoryEmotion.SHOCKING) }
            .verifyComplete()
    }

    @Test
    @DisplayName("알 수 없는 emotion 값은 무시하고 null로 처리한다")
    fun extractMemories_ignoresUnknownEmotionValue() {
        // given - 정의되지 않은 임의의 감정 문자열("UNKNOWN_VALUE")을 가진 LLM 응답 설정
        val sessionIdObj = ConversationSessionFixture.createId()
        val sessionId = sessionIdObj.value
        val context =
            MemoryExtractionContext.of(
                sessionId,
                listOf(ConversationSnippet("그냥 평범한 하루였어", null)),
                emptyList(),
            )
        val response =
            """[{"type":"FACTUAL","content":"사용자는 평범한 하루를 보냈다","importance":0.2,"reasoning":"일상","emotion":"UNKNOWN_VALUE"}]"""

        `when`(llmPort.complete(anyValue())).thenReturn(Mono.just(response))

        // when & then - 정의되지 않은 감정은 에러를 던지지 않고 안전하게 null로 포백 처리되는지 검증
        StepVerifier
            .create(adapter.extractMemories(context))
            .assertNext { result -> assertThat(result.emotion).isNull() }
            .verifyComplete()
    }
}
