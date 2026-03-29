package com.miyou.app.application.dialogue.pipeline.stage;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.miyou.app.application.credit.usecase.CreditDeductUseCase;
import com.miyou.app.application.dialogue.pipeline.PipelineInputs;
import com.miyou.app.application.memory.policy.MemoryExtractionPolicy;
import com.miyou.app.application.memory.service.MemoryExtractionService;
import com.miyou.app.application.monitoring.context.PipelineContext;
import com.miyou.app.application.monitoring.port.ConversationMetricsPort;
import com.miyou.app.application.monitoring.service.PipelineTracer;
import com.miyou.app.domain.dialogue.model.ConversationTurn;
import com.miyou.app.domain.dialogue.port.ConversationRepository;
import com.miyou.app.domain.dialogue.port.LlmPort;
import com.miyou.app.domain.dialogue.port.TokenUsageProvider;
import com.miyou.app.domain.memory.port.ConversationCounterPort;
import com.miyou.app.domain.monitoring.model.DialoguePipelineStage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.ContextView;

@Slf4j
@Service
public class DialoguePostProcessingService {

	private final ConversationRepository conversationRepository;
	private final ConversationCounterPort conversationCounterPort;
	private final MemoryExtractionService memoryExtractionService;
	private final LlmPort llmPort;
	private final PipelineTracer pipelineTracer;
	private final ConversationMetricsPort conversationMetrics;
	private final CreditDeductUseCase creditDeductUseCase;
	private final int conversationThreshold;

	public DialoguePostProcessingService(ConversationRepository conversationRepository,
		ConversationCounterPort conversationCounterPort,
		MemoryExtractionService memoryExtractionService,
		LlmPort llmPort,
		PipelineTracer pipelineTracer,
		ConversationMetricsPort conversationMetrics,
		CreditDeductUseCase creditDeductUseCase,
		MemoryExtractionPolicy policy) {
		this.conversationRepository = conversationRepository;
		this.conversationCounterPort = conversationCounterPort;
		this.memoryExtractionService = memoryExtractionService;
		this.llmPort = llmPort;
		this.pipelineTracer = pipelineTracer;
		this.conversationMetrics = conversationMetrics;
		this.creditDeductUseCase = creditDeductUseCase;
		this.conversationThreshold = policy.conversationThreshold();
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
	 * 응답 저장, 크레딧 차감, 대화 카운트 증가, 임계 회차 도달 시 메모리 추출을 순서대로 수행합니다. 크레딧 차감은 오디오가 클라이언트에게 완전히 전달된 이후에만 호출되므로, 파이프라인이 중단되면 차감되지
	 * 않습니다.
	 */
	private Mono<Void> persistConversationAndMaybeExtract(Mono<PipelineInputs> inputsMono,
		Mono<String> responseMono) {
		return inputsMono.flatMap(inputs -> {
			var session = inputs.session();
			var sessionId = session.sessionId();
			return responseMono.flatMap(response -> {
				conversationMetrics.recordQueryLength(inputs.currentTurn().query().length());
				conversationMetrics.recordResponseLength(response.length());
				return persistConversation(inputsMono, response);
			})
				.flatMap(turn -> deductCreditSafely(session.userId(), sessionId))
				.flatMap(ignored -> conversationCounterPort.increment(sessionId))
				.doOnNext(count -> {
					conversationMetrics.recordConversationIncrement();
					conversationMetrics.recordConversationCount(count);
				})
				.filter(count -> count % conversationThreshold == 0)
				.flatMap(count -> memoryExtractionService.checkAndExtract(sessionId));
		}).subscribeOn(Schedulers.boundedElastic()).then();
	}

	/**
	 * 크레딧 차감을 시도하고, 실패해도 파이프라인 전체가 중단되지 않도록 에러를 흡수합니다. 잔액 부족이나 트랜잭션 충돌은 로그로 기록하고 관리자가 별도로 처리합니다.
	 */
	private Mono<Object> deductCreditSafely(
		com.miyou.app.domain.dialogue.model.UserId userId,
		com.miyou.app.domain.dialogue.model.ConversationSessionId sessionId) {
		return creditDeductUseCase.deductForConversation(userId, sessionId)
			.cast(Object.class)
			.onErrorResume(e -> {
				log.error("크레딧 차감 실패 - userId={}, sessionId={}: {}",
					userId.value(),
					sessionId.value(),
					e.getMessage());
				return Mono.just(Boolean.FALSE);
			});
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
