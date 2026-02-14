package com.study.webflux.rag.application.dialogue.pipeline.stage;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.dialogue.pipeline.PipelineInputs;
import com.study.webflux.rag.application.memory.service.MemoryExtractionService;
import com.study.webflux.rag.application.monitoring.context.PipelineContext;
import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.dialogue.port.LlmPort;
import com.study.webflux.rag.domain.dialogue.port.TokenUsageProvider;
import com.study.webflux.rag.domain.memory.port.ConversationCounterPort;
import com.study.webflux.rag.domain.monitoring.model.DialoguePipelineStage;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.ContextView;

@Service
public class DialoguePostProcessingService {

	private final ConversationRepository conversationRepository;
	private final ConversationCounterPort conversationCounterPort;
	private final MemoryExtractionService memoryExtractionService;
	private final LlmPort llmPort;
	private final PipelineTracer pipelineTracer;
	private final int conversationThreshold;

	public DialoguePostProcessingService(ConversationRepository conversationRepository,
		ConversationCounterPort conversationCounterPort,
		MemoryExtractionService memoryExtractionService,
		LlmPort llmPort,
		PipelineTracer pipelineTracer,
		RagDialogueProperties properties) {
		this.conversationRepository = conversationRepository;
		this.conversationCounterPort = conversationCounterPort;
		this.memoryExtractionService = memoryExtractionService;
		this.llmPort = llmPort;
		this.pipelineTracer = pipelineTracer;
		this.conversationThreshold = properties.getMemory().getConversationThreshold();
		if (this.conversationThreshold <= 0) {
			throw new IllegalArgumentException("conversationThreshold 설정값은 0 이하일 수 없습니다.");
		}
	}

	/**
	 * 오디오 응답용 문장 스트림을 하나의 텍스트로 저장하고, 설정된 회차마다 메모리 추출을 수행합니다.
	 */
	public Mono<Void> persistAndExtract(Mono<PipelineInputs> inputsMono, Flux<String> sentences) {
		Mono<String> responseMono = sentences.collectList().map(tokens -> String.join(" ", tokens));
		return persistConversationAndMaybeExtract(inputsMono, responseMono);
	}

	/**
	 * 텍스트 전용 토큰 스트림을 저장하고 토큰 사용량 메트릭을 기록한 뒤 메모리 추출을 수행합니다.
	 */
	public Mono<Void> persistAndExtractText(Mono<PipelineInputs> inputsMono,
		Flux<String> textStream) {
		Mono<String> responseMono = textStream.collectList()
			.flatMap(tokens -> Mono.deferContextual(contextView -> {
				recordTokenUsageIfAvailable(contextView);
				return Mono.just(String.join("", tokens));
			}));
		return persistConversationAndMaybeExtract(inputsMono, responseMono);
	}

	/**
	 * 응답 저장 이후 대화 카운트를 증가시키고, 임계 회차 도달 시 메모리를 추출합니다.
	 */
	private Mono<Void> persistConversationAndMaybeExtract(Mono<PipelineInputs> inputsMono,
		Mono<String> responseMono) {
		return inputsMono.flatMap(inputs -> {
			var userId = inputs.userId();
			return responseMono.flatMap(response -> persistConversation(inputsMono, response))
				.flatMap(turn -> conversationCounterPort.increment(userId))
				.filter(count -> count % conversationThreshold == 0)
				.flatMap(count -> memoryExtractionService.checkAndExtract(userId));
		}).subscribeOn(Schedulers.boundedElastic()).then();
	}

	/**
	 * 현재 턴에 모델 응답을 병합해 영속 저장합니다.
	 */
	private Mono<ConversationTurn> persistConversation(Mono<PipelineInputs> inputsMono,
		String fullResponse) {
		return inputsMono.flatMap(inputs -> pipelineTracer.tracePersistence(
			() -> conversationRepository.save(inputs.currentTurn().withResponse(fullResponse))));
	}

	/**
	 * LLM 어댑터가 토큰 사용량을 제공하는 경우 파이프라인 메트릭에 prompt/completion/total 값을 기록합니다.
	 */
	private void recordTokenUsageIfAvailable(ContextView contextView) {
		if (!(llmPort instanceof TokenUsageProvider tokenUsageProvider)) {
			return;
		}
		var tracker = PipelineContext.findTracker(contextView);
		if (tracker == null) {
			return;
		}
		tokenUsageProvider.getTokenUsage(tracker.pipelineId()).ifPresent(tokenUsage -> {
			tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION,
				"promptTokens",
				tokenUsage.promptTokens());
			tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION,
				"completionTokens",
				tokenUsage.completionTokens());
			tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION,
				"totalTokens",
				tokenUsage.totalTokens());
		});
	}
}
