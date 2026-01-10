package com.study.webflux.rag.application.dialogue.pipeline.stage;

import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.port.RetrievalPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DialogueInputServiceTest {

	@Mock
	private RetrievalPort retrievalPort;

	@Mock
	private ConversationRepository conversationRepository;

	@Mock
	private PipelineTracer pipelineTracer;

	private DialogueInputService service;

	@BeforeEach
	void setUp() {
		service = new DialogueInputService(retrievalPort, conversationRepository, pipelineTracer);
	}

	@Test
	@DisplayName("입력 준비 시 검색 컨텍스트, 메모리, 대화 이력을 포함한다")
	void prepareInputs_shouldReturnPipelineInputsWithAllComponents() {
		UserId userId = UserId.of("user-1");
		String query = "안녕하세요";

		RetrievalContext retrievalContext = RetrievalContext.empty(query);
		MemoryRetrievalResult memories = MemoryRetrievalResult.empty();
		ConversationTurn previousTurn = ConversationTurn.create(userId, "이전 질문")
			.withResponse("이전 응답");

		when(pipelineTracer.traceRetrieval(any())).thenAnswer(invocation -> {
			return Mono.just(retrievalContext);
		});
		when(pipelineTracer.traceMemories(any())).thenAnswer(invocation -> {
			return Mono.just(memories);
		});
		when(conversationRepository.findRecent(eq(userId), anyInt()))
			.thenReturn(Flux.just(previousTurn));

		StepVerifier.create(service.prepareInputs(userId, query))
			.assertNext(inputs -> {
				assertThat(inputs.userId()).isEqualTo(userId);
				assertThat(inputs.retrievalContext()).isEqualTo(retrievalContext);
				assertThat(inputs.memories()).isEqualTo(memories);
				assertThat(inputs.conversationContext()).isNotNull();
				assertThat(inputs.currentTurn()).isNotNull();
				assertThat(inputs.currentTurn().query()).isEqualTo(query);
			})
			.verifyComplete();
	}

	@Test
	@DisplayName("대화 이력이 없으면 빈 컨텍스트를 반환한다")
	void prepareInputs_withEmptyHistory_shouldReturnEmptyContext() {
		UserId userId = UserId.of("user-1");
		String query = "첫 질문";

		when(pipelineTracer.traceRetrieval(any())).thenAnswer(invocation -> {
			return Mono.just(RetrievalContext.empty(query));
		});
		when(pipelineTracer.traceMemories(any())).thenAnswer(invocation -> {
			return Mono.just(MemoryRetrievalResult.empty());
		});
		when(conversationRepository.findRecent(eq(userId), anyInt()))
			.thenReturn(Flux.empty());

		StepVerifier.create(service.prepareInputs(userId, query))
			.assertNext(inputs -> {
				assertThat(inputs.conversationContext().turns()).isEmpty();
			})
			.verifyComplete();
	}

	@Test
	@DisplayName("검색 실패 시 에러를 전파한다")
	void prepareInputs_withRetrievalError_shouldPropagateError() {
		UserId userId = UserId.of("user-1");
		String query = "실패 테스트";

		when(pipelineTracer.traceRetrieval(any())).thenAnswer(invocation -> {
			return Mono.error(new RuntimeException("검색 실패"));
		});
		when(pipelineTracer.traceMemories(any())).thenAnswer(invocation -> {
			return Mono.just(MemoryRetrievalResult.empty());
		});
		when(conversationRepository.findRecent(eq(userId), anyInt()))
			.thenReturn(Flux.empty());

		StepVerifier.create(service.prepareInputs(userId, query))
			.expectErrorMatches(error -> error.getMessage().contains("검색 실패"))
			.verify();
	}

	@Test
	@DisplayName("검색과 메모리 조회를 병렬로 실행한다")
	void prepareInputs_shouldExecuteRetrievalAndMemoryInParallel() {
		UserId userId = UserId.of("user-1");
		String query = "병렬 테스트";

		when(pipelineTracer.traceRetrieval(any())).thenAnswer(invocation -> {
			return Mono.just(RetrievalContext.empty(query));
		});
		when(pipelineTracer.traceMemories(any())).thenAnswer(invocation -> {
			return Mono.just(MemoryRetrievalResult.empty());
		});
		when(conversationRepository.findRecent(eq(userId), anyInt()))
			.thenReturn(Flux.empty());

		StepVerifier.create(service.prepareInputs(userId, query))
			.assertNext(inputs -> {
				assertThat(inputs).isNotNull();
			})
			.verifyComplete();

		verify(pipelineTracer).traceRetrieval(any());
		verify(pipelineTracer).traceMemories(any());
	}
}
