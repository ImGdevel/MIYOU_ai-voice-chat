package com.study.webflux.rag.infrastructure.memory.adapter;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.webflux.rag.domain.dialogue.model.CompletionRequest;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.port.LlmPort;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryExtractionContext;
import com.study.webflux.rag.domain.memory.model.MemoryType;
import com.study.webflux.rag.fixture.ConversationSessionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmMemoryExtractionAdapterTest {

	@Mock
	private LlmPort llmPort;

	private ObjectMapper objectMapper;

	private MemoryExtractionConfig config;

	private LlmMemoryExtractionAdapter adapter;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		config = new MemoryExtractionConfig("gpt-4o-mini", 5, 0.2f, 0.3f);
		adapter = new LlmMemoryExtractionAdapter(llmPort, objectMapper, config);
	}

	@Test
	@DisplayName("대화에서 메모리 추출 성공")
	void extractMemories_success() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn1 = ConversationTurn.withId("id-1",
			sessionId,
			"나는 서울에 살아",
			"반갑습니다!",
			Instant.now());
		MemoryExtractionContext context = MemoryExtractionContext.of(sessionId,
			List.of(turn1),
			List.of());

		String llmResponse = """
			[
			{
				"type": "FACTUAL",
				"content": "사용자는 서울에 거주한다",
				"importance": 0.7,
				"reasoning": "거주지 정보는 중요한 사실"
			}
			]
			""";

		when(llmPort.complete(any(CompletionRequest.class))).thenReturn(Mono.just(llmResponse));

		StepVerifier.create(adapter.extractMemories(context)).assertNext(result -> {
			assertThat(result.sessionId()).isEqualTo(sessionId);
			assertThat(result.type()).isEqualTo(MemoryType.FACTUAL);
			assertThat(result.content()).isEqualTo("사용자는 서울에 거주한다");
			assertThat(result.importance()).isEqualTo(0.7f);
			assertThat(result.reasoning()).isEqualTo("거주지 정보는 중요한 사실");
		}).verifyComplete();

		verify(llmPort).complete(any(CompletionRequest.class));
	}

	@Test
	@DisplayName("여러 메모리 추출 성공")
	void extractMemories_multiple() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "나는 커피를 좋아하고 개발자로 일해");
		MemoryExtractionContext context = MemoryExtractionContext.of(sessionId,
			List.of(turn),
			List.of());

		String llmResponse = """
			[
			{
				"type": "FACTUAL",
				"content": "사용자는 커피를 선호한다",
				"importance": 0.5,
				"reasoning": "음료 선호도"
			},
			{
				"type": "FACTUAL",
				"content": "사용자는 개발자로 근무한다",
				"importance": 0.8,
				"reasoning": "직업 정보는 중요"
			}
			]
			""";

		when(llmPort.complete(any(CompletionRequest.class))).thenReturn(Mono.just(llmResponse));

		StepVerifier.create(adapter.extractMemories(context))
			.assertNext(result -> assertThat(result.content()).isEqualTo("사용자는 커피를 선호한다"))
			.assertNext(result -> assertThat(result.content()).isEqualTo("사용자는 개발자로 근무한다"))
			.verifyComplete();
	}

	@Test
	@DisplayName("경험적 메모리 추출 성공")
	void extractMemories_experiential() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "어제 친구들과 등산을 다녀왔어");
		MemoryExtractionContext context = MemoryExtractionContext.of(sessionId,
			List.of(turn),
			List.of());

		String llmResponse = """
			[
			{
				"type": "EXPERIENTIAL",
				"content": "사용자는 최근 친구들과 등산을 했다",
				"importance": 0.6,
				"reasoning": "최근 활동 기록"
			}
			]
			""";

		when(llmPort.complete(any(CompletionRequest.class))).thenReturn(Mono.just(llmResponse));

		StepVerifier.create(adapter.extractMemories(context)).assertNext(result -> {
			assertThat(result.type()).isEqualTo(MemoryType.EXPERIENTIAL);
			assertThat(result.content()).contains("등산");
		}).verifyComplete();
	}

	@Test
	@DisplayName("빈 배열 응답 시 빈 Flux 반환")
	void extractMemories_emptyArray() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "안녕");
		MemoryExtractionContext context = MemoryExtractionContext.of(sessionId,
			List.of(turn),
			List.of());

		when(llmPort.complete(any(CompletionRequest.class))).thenReturn(Mono.just("[]"));

		StepVerifier.create(adapter.extractMemories(context)).verifyComplete();
	}

	@Test
	@DisplayName("JSON 마크다운 코드 블록 파싱 성공")
	void extractMemories_jsonMarkdown() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "테스트");
		MemoryExtractionContext context = MemoryExtractionContext.of(sessionId,
			List.of(turn),
			List.of());

		String llmResponse = """
			```json
			[
			{
				"type": "FACTUAL",
				"content": "테스트 메모리",
				"importance": 0.5,
				"reasoning": "테스트"
			}
			]
			```
			""";

		when(llmPort.complete(any(CompletionRequest.class))).thenReturn(Mono.just(llmResponse));

		StepVerifier.create(adapter.extractMemories(context))
			.assertNext(result -> assertThat(result.content()).isEqualTo("테스트 메모리"))
			.verifyComplete();
	}

	@Test
	@DisplayName("기존 메모리를 포함한 컨텍스트로 추출")
	void extractMemories_withExistingMemories() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "나는 이제 부산에 살아");
		Memory existingMemory = new Memory("mem-1", sessionId, MemoryType.FACTUAL, "사용자는 서울에 거주한다",
			0.7f, Instant.now(), Instant.now(), 1);
		MemoryExtractionContext context = MemoryExtractionContext.of(sessionId,
			List.of(turn),
			List.of(existingMemory));

		String llmResponse = """
			[
			{
				"type": "FACTUAL",
				"content": "사용자는 부산으로 이사했다",
				"importance": 0.8,
				"reasoning": "거주지 변경은 중요"
			}
			]
			""";

		when(llmPort.complete(any(CompletionRequest.class))).thenReturn(Mono.just(llmResponse));

		StepVerifier.create(adapter.extractMemories(context))
			.assertNext(result -> assertThat(result.content()).contains("부산"))
			.verifyComplete();
	}

	@Test
	@DisplayName("중요도 0.0 ~ 1.0 범위 검증")
	void extractMemories_importanceRange() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "테스트");
		MemoryExtractionContext context = MemoryExtractionContext.of(sessionId,
			List.of(turn),
			List.of());

		String llmResponse = """
			[
			{
				"type": "FACTUAL",
				"content": "최소 중요도",
				"importance": 0.0,
				"reasoning": "테스트"
			},
			{
				"type": "FACTUAL",
				"content": "최대 중요도",
				"importance": 1.0,
				"reasoning": "테스트"
			}
			]
			""";

		when(llmPort.complete(any(CompletionRequest.class))).thenReturn(Mono.just(llmResponse));

		StepVerifier.create(adapter.extractMemories(context))
			.assertNext(result -> assertThat(result.importance()).isEqualTo(0.0f))
			.assertNext(result -> assertThat(result.importance()).isEqualTo(1.0f))
			.verifyComplete();
	}

	@Test
	@DisplayName("잘못된 JSON 응답 시 빈 Flux 반환")
	void extractMemories_invalidJson_returnsEmpty() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "테스트");
		MemoryExtractionContext context = MemoryExtractionContext.of(sessionId,
			List.of(turn),
			List.of());

		when(llmPort.complete(any(CompletionRequest.class))).thenReturn(
			Mono.just("invalid json"));

		StepVerifier.create(adapter.extractMemories(context)).verifyComplete();
	}

	@Test
	@DisplayName("LLM 에러 시 에러 전파")
	void extractMemories_llmError_propagates() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "테스트");
		MemoryExtractionContext context = MemoryExtractionContext.of(sessionId,
			List.of(turn),
			List.of());

		when(llmPort.complete(any(CompletionRequest.class))).thenReturn(
			Mono.error(new RuntimeException("LLM API error")));

		StepVerifier.create(adapter.extractMemories(context))
			.expectErrorMatches(throwable -> throwable.getMessage().contains("LLM API error"))
			.verify();
	}

	@Test
	@DisplayName("여러 대화 턴을 컨텍스트로 전달")
	void extractMemories_multipleTurns() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn1 = ConversationTurn.withId("id-1",
			sessionId,
			"나는 개발자야",
			"좋아요",
			Instant.now());
		ConversationTurn turn2 = ConversationTurn.withId("id-2",
			sessionId,
			"자바를 주로 사용해",
			"알겠습니다",
			Instant.now());
		MemoryExtractionContext context = MemoryExtractionContext.of(sessionId,
			List.of(turn1, turn2),
			List.of());

		String llmResponse = """
			[
			{
				"type": "FACTUAL",
				"content": "사용자는 자바 개발자다",
				"importance": 0.8,
				"reasoning": "직업과 기술 스택"
			}
			]
			""";

		when(llmPort.complete(any(CompletionRequest.class))).thenReturn(Mono.just(llmResponse));

		StepVerifier.create(adapter.extractMemories(context)).assertNext(result -> {
			assertThat(result.content()).contains("자바");
			assertThat(result.type()).isEqualTo(MemoryType.FACTUAL);
		}).verifyComplete();
	}
}
