package com.study.webflux.rag.application.dialogue.pipeline.stage;

import com.study.webflux.rag.application.dialogue.pipeline.PipelineInputs;
import com.study.webflux.rag.application.memory.service.MemoryExtractionService;
import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.model.ConversationSession;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.dialogue.port.LlmPort;
import com.study.webflux.rag.domain.memory.port.ConversationCounterPort;
import com.study.webflux.rag.fixture.ConversationSessionFixture;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import com.study.webflux.rag.infrastructure.monitoring.config.ConversationMetricsConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DialoguePostProcessingServiceTest {

	@Mock
	private ConversationRepository conversationRepository;

	@Mock
	private ConversationCounterPort conversationCounterPort;

	@Mock
	private MemoryExtractionService memoryExtractionService;

	@Mock
	private LlmPort llmPort;

	@Mock
	private PipelineTracer pipelineTracer;

	@Mock
	private ConversationMetricsConfiguration conversationMetricsConfiguration;

	private DialoguePostProcessingService service;

	@BeforeEach
	void setUp() {
		RagDialogueProperties properties = new RagDialogueProperties();
		RagDialogueProperties.Memory memoryProps = new RagDialogueProperties.Memory();
		memoryProps.setConversationThreshold(5);
		properties.setMemory(memoryProps);

		service = new DialoguePostProcessingService(
			conversationRepository,
			conversationCounterPort,
			memoryExtractionService,
			llmPort,
			pipelineTracer,
			conversationMetricsConfiguration,
			properties);
	}

	@Test
	@DisplayName("대화 저장 시 응답을 포함하여 저장한다")
	void persistAndExtract_shouldSaveConversationWithResponse() {
		ConversationSession session = ConversationSessionFixture.create();
		ConversationSessionId sessionId = session.sessionId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "질문");
		PipelineInputs inputs = new PipelineInputs(session, null, null, null, turn);

		when(pipelineTracer.tracePersistence(any()))
			.thenAnswer(invocation -> Mono.just(turn.withResponse("응답 문장1 응답 문장2")));
		when(conversationCounterPort.increment(sessionId)).thenReturn(Mono.just(1L));

		StepVerifier
			.create(service.persistAndExtract(Mono.just(inputs), Flux.just("응답 문장1", "응답 문장2")))
			.verifyComplete();

		verify(pipelineTracer).tracePersistence(any());
		verify(conversationCounterPort).increment(sessionId);
	}

	@Test
	@DisplayName("대화 저장 후 카운터를 증가시킨다")
	void persistAndExtract_shouldIncrementConversationCounter() {
		ConversationSession session = ConversationSessionFixture.create();
		ConversationSessionId sessionId = session.sessionId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "질문");
		PipelineInputs inputs = new PipelineInputs(session, null, null, null, turn);

		when(pipelineTracer.tracePersistence(any()))
			.thenAnswer(invocation -> Mono.just(turn.withResponse("응답")));
		when(conversationCounterPort.increment(sessionId)).thenReturn(Mono.just(3L));

		StepVerifier.create(service.persistAndExtract(Mono.just(inputs), Flux.just("응답")))
			.verifyComplete();

		verify(conversationCounterPort).increment(sessionId);
	}

	@Test
	@DisplayName("임계값 도달 시 메모리 추출을 실행한다")
	void persistAndExtract_shouldTriggerMemoryExtractionAtThreshold() {
		ConversationSession session = ConversationSessionFixture.create();
		ConversationSessionId sessionId = session.sessionId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "질문");
		PipelineInputs inputs = new PipelineInputs(session, null, null, null, turn);

		when(pipelineTracer.tracePersistence(any()))
			.thenAnswer(invocation -> Mono.just(turn.withResponse("응답")));
		when(conversationCounterPort.increment(sessionId)).thenReturn(Mono.just(5L));
		when(memoryExtractionService.checkAndExtract(sessionId)).thenReturn(Mono.empty());

		StepVerifier.create(service.persistAndExtract(Mono.just(inputs), Flux.just("응답")))
			.verifyComplete();

		verify(memoryExtractionService).checkAndExtract(sessionId);
	}

	@Test
	@DisplayName("임계값 미만이면 메모리 추출을 건너뛴다")
	void persistAndExtract_shouldNotTriggerMemoryExtractionBelowThreshold() {
		ConversationSession session = ConversationSessionFixture.create();
		ConversationSessionId sessionId = session.sessionId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "질문");
		PipelineInputs inputs = new PipelineInputs(session, null, null, null, turn);

		when(pipelineTracer.tracePersistence(any()))
			.thenAnswer(invocation -> Mono.just(turn.withResponse("응답")));
		when(conversationCounterPort.increment(sessionId)).thenReturn(Mono.just(3L));

		StepVerifier.create(service.persistAndExtract(Mono.just(inputs), Flux.just("응답")))
			.verifyComplete();

		verify(memoryExtractionService, never()).checkAndExtract(any());
	}

	@Test
	@DisplayName("conversationThreshold가 0 이하면 예외를 발생시킨다")
	void constructor_withInvalidThreshold_shouldThrowException() {
		RagDialogueProperties properties = new RagDialogueProperties();
		RagDialogueProperties.Memory memoryProps = new RagDialogueProperties.Memory();
		memoryProps.setConversationThreshold(0);
		properties.setMemory(memoryProps);

		assertThatThrownBy(() -> new DialoguePostProcessingService(
			conversationRepository,
			conversationCounterPort,
			memoryExtractionService,
			llmPort,
			pipelineTracer,
			conversationMetricsConfiguration,
			properties))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationThreshold");
	}
}
