package com.miyou.app.infrastructure.memory.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import com.miyou.app.domain.dialogue.model.CompletionRequest
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.LlmPort
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryExtractionContext
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.fixture.ConversationSessionFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
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
		adapter = LlmMemoryExtractionAdapter(
			llmPort,
			ObjectMapper(),
			MemoryExtractionConfig("gpt-4o-mini", 5, 0.2f, 0.3f),
		)
	}

	@Test
	@DisplayName("대화에서 메모리를 추출한다")
	fun extractMemories_success() {
		val sessionId = ConversationSessionFixture.createId()
		val context = MemoryExtractionContext.of(
			sessionId,
			listOf(ConversationTurn.withId("id-1", sessionId, "나는 서울에 살아", "반갑습니다", Instant.now())),
			listOf(),
		)
		val llmResponse =
			"""[{"type":"FACTUAL","content":"사용자는 서울에 거주한다","importance":0.7,"reasoning":"거주지 정보"}]"""

		`when`(llmPort.complete(any(CompletionRequest::class.java))).thenReturn(Mono.just(llmResponse))

		StepVerifier.create(adapter.extractMemories(context))
			.assertNext { result ->
				assertThat(result.sessionId()).isEqualTo(sessionId)
				assertThat(result.type()).isEqualTo(MemoryType.FACTUAL)
				assertThat(result.content()).contains("서울")
			}
			.verifyComplete()

		verify(llmPort).complete(any(CompletionRequest::class.java))
	}

	@Test
	@DisplayName("여러 메모리를 추출할 수 있다")
	fun extractMemories_multiple() {
		val sessionId = ConversationSessionFixture.createId()
		val context = MemoryExtractionContext.of(
			sessionId,
			listOf(ConversationTurn.create(sessionId, "나는 커피를 좋아하고 개발자로 일해")),
			listOf(),
		)
		val response =
			"""[{"type":"FACTUAL","content":"사용자는 커피를 좋아한다","importance":0.5,"reasoning":"취향"},{"type":"FACTUAL","content":"사용자는 개발자로 일한다","importance":0.8,"reasoning":"직업"}]"""

		`when`(llmPort.complete(any(CompletionRequest::class.java))).thenReturn(Mono.just(response))

		StepVerifier.create(adapter.extractMemories(context))
			.expectNextCount(2)
			.verifyComplete()
	}

	@Test
	@DisplayName("markdown json 블록도 파싱한다")
	fun extractMemories_jsonMarkdown() {
		val sessionId = ConversationSessionFixture.createId()
		val context = MemoryExtractionContext.of(sessionId, listOf(ConversationTurn.create(sessionId, "테스트")), listOf())
		val response = """
			```json
			[{"type":"FACTUAL","content":"테스트 메모리","importance":0.5,"reasoning":"테스트"}]
			```
		""".trimIndent()

		`when`(llmPort.complete(any(CompletionRequest::class.java))).thenReturn(Mono.just(response))

		StepVerifier.create(adapter.extractMemories(context))
			.assertNext { result -> assertThat(result.content()).isEqualTo("테스트 메모리") }
			.verifyComplete()
	}

	@Test
	@DisplayName("잘못된 JSON이면 빈 Flux를 반환한다")
	fun extractMemories_invalidJson_returnsEmpty() {
		val sessionId = ConversationSessionFixture.createId()
		val context = MemoryExtractionContext.of(sessionId, listOf(ConversationTurn.create(sessionId, "테스트")), listOf())
		`when`(llmPort.complete(any(CompletionRequest::class.java))).thenReturn(Mono.just("invalid json"))

		StepVerifier.create(adapter.extractMemories(context)).verifyComplete()
	}

	@Test
	@DisplayName("existing memories를 포함한 컨텍스트도 처리한다")
	fun extractMemories_withExistingMemories() {
		val sessionId = ConversationSessionFixture.createId()
		val existingMemory = Memory("mem-1", sessionId, MemoryType.FACTUAL, "사용자는 서울에 거주한다", 0.7f, Instant.now(), Instant.now(), 1)
		val context = MemoryExtractionContext.of(
			sessionId,
			listOf(ConversationTurn.create(sessionId, "나는 이제 부산에 살아")),
			listOf(existingMemory),
		)
		val response =
			"""[{"type":"FACTUAL","content":"사용자는 부산으로 이사했다","importance":0.8,"reasoning":"거주지 변경"}]"""

		`when`(llmPort.complete(any(CompletionRequest::class.java))).thenReturn(Mono.just(response))

		StepVerifier.create(adapter.extractMemories(context))
			.assertNext { result -> assertThat(result.content()).contains("부산") }
			.verifyComplete()
	}
}
