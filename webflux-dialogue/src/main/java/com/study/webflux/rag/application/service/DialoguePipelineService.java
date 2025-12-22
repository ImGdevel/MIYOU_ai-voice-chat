package com.study.webflux.rag.application.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.monitoring.DialoguePipelineMonitor;
import com.study.webflux.rag.application.monitoring.DialoguePipelineStage;
import com.study.webflux.rag.application.monitoring.DialoguePipelineTracker;
import com.study.webflux.rag.domain.model.conversation.ConversationContext;
import com.study.webflux.rag.domain.model.conversation.ConversationTurn;
import com.study.webflux.rag.domain.model.llm.CompletionRequest;
import com.study.webflux.rag.domain.model.llm.Message;
import com.study.webflux.rag.domain.model.memory.MemoryRetrievalResult;
import com.study.webflux.rag.domain.model.rag.RetrievalContext;
import com.study.webflux.rag.domain.port.in.DialoguePipelineUseCase;
import com.study.webflux.rag.domain.port.out.ConversationCounterPort;
import com.study.webflux.rag.domain.port.out.ConversationRepository;
import com.study.webflux.rag.domain.port.out.LlmPort;
import com.study.webflux.rag.domain.port.out.RetrievalPort;
import com.study.webflux.rag.domain.port.out.TokenUsageProvider;
import com.study.webflux.rag.domain.port.out.TtsPort;
import com.study.webflux.rag.domain.service.SentenceAssembler;
import com.study.webflux.rag.infrastructure.config.properties.RagDialogueProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
public class DialoguePipelineService implements DialoguePipelineUseCase {

	/**
	 * 리액티브 대화 파이프라인을 총괄합니다. 입력 텍스트를 받아 메모리/검색 컨텍스트를 준비하고, 시스템 프롬프트를 구성한 뒤 LLM 토큰 스트리밍과 TTS 스트리밍까지 이어지는 전체 흐름을 관리합니다.
	 */
	private final LlmPort llmPort;
	private final TtsPort ttsPort;
	private final RetrievalPort retrievalPort;
	private final ConversationRepository conversationRepository;
	private final SentenceAssembler sentenceAssembler;
	private final DialoguePipelineMonitor pipelineMonitor;
	private final ConversationCounterPort conversationCounterPort;
	private final MemoryExtractionService memoryExtractionService;
	private final SystemPromptService systemPromptService;
	private final PipelineTracer pipelineTracer;
	private final String llmModel;
	private final int conversationThreshold;

	public DialoguePipelineService(LlmPort llmPort,
		TtsPort ttsPort,
		RetrievalPort retrievalPort,
		ConversationRepository conversationRepository,
		SentenceAssembler sentenceAssembler,
		DialoguePipelineMonitor pipelineMonitor,
		ConversationCounterPort conversationCounterPort,
		MemoryExtractionService memoryExtractionService,
		SystemPromptService systemPromptService,
		PipelineTracer pipelineTracer,
		RagDialogueProperties properties) {
		this.llmPort = llmPort;
		this.ttsPort = ttsPort;
		this.retrievalPort = retrievalPort;
		this.conversationRepository = conversationRepository;
		this.sentenceAssembler = sentenceAssembler;
		this.pipelineMonitor = pipelineMonitor;
		this.conversationCounterPort = conversationCounterPort;
		this.memoryExtractionService = memoryExtractionService;
		this.systemPromptService = systemPromptService;
		this.pipelineTracer = pipelineTracer;
		this.llmModel = properties.getOpenai().getModel();
		this.conversationThreshold = properties.getMemory().getConversationThreshold();
	}

	/**
	 * 텍스트 입력을 받아 오디오 바이트 스트림을 반환합니다. 응답 생성이 완료된 뒤에만 쿼리를 저장해 중간 실패 시 저장을 피합니다.
	 */
	@Override
	public Flux<byte[]> executeAudioStreaming(String text) {
		DialoguePipelineTracker tracker = pipelineMonitor.create(text);

		// TTS 준비
		Mono<Void> ttsWarmup = pipelineTracer.traceTtsPreparation(tracker,
			() -> ttsPort.prepare().doOnError(error -> log
				.warn("파이프라인 {}의 TTS 준비 실패: {}", tracker.pipelineId(), error.getMessage()))
				.onErrorResume(error -> Mono.empty()))
			.cache();

		ttsWarmup.subscribe();

		Mono<PipelineInputs> inputsMono = prepareInputs(text, tracker).cache();

		// LLM 토큰 생성
		Flux<String> llmTokens = streamLlmTokens(tracker, inputsMono).subscribeOn(
			Schedulers.boundedElastic()).doOnNext(
				token -> pipelineTracer.increment(tracker,
					DialoguePipelineStage.LLM_COMPLETION,
					"tokenCount",
					1));

		// 문장 어셈블
		Flux<String> sentences = pipelineTracer.traceSentenceAssembly(tracker,
			() -> sentenceAssembler.assemble(llmTokens),
			sentence -> {
				tracker.recordLlmOutput(sentence);
				log.debug("Sentence: [{}]", sentence);
			}).share();

		// 오디오 스트림 생성
		Flux<byte[]> audioFlux = sentences.publish(sharedSentences -> {
			Flux<String> cachedSentences = sharedSentences.replay().autoConnect(2);

			cachedSentences.collectList().flatMap(sentenceList -> {
				String fullResponse = String.join(" ", sentenceList);
				return inputsMono.flatMap(inputs -> pipelineTracer.tracePersistence(tracker,
					() -> conversationRepository
						.save(inputs.currentTurn().withResponse(fullResponse))));
			}).subscribe();

			Mono<String> firstSentenceMono = cachedSentences.take(1).singleOrEmpty().cache();
			Flux<String> remainingSentences = cachedSentences.skip(1);

			Flux<byte[]> firstSentenceAudio = firstSentenceMono
				.flatMapMany(sentence -> ttsWarmup.thenMany(ttsPort.streamSynthesize(sentence)))
				.publishOn(Schedulers.boundedElastic());

			Flux<byte[]> remainingAudio = remainingSentences.publishOn(Schedulers.boundedElastic())
				.concatMap(sentence -> ttsWarmup.thenMany(ttsPort.streamSynthesize(sentence)));

			return Flux.mergeSequential(firstSentenceAudio, remainingAudio);
		});

		// 오디오 스트림 추적
		Flux<byte[]> audioStream = pipelineTracer
			.traceTtsSynthesis(tracker, () -> audioFlux, () -> {
				tracker
					.incrementStageCounter(DialoguePipelineStage.TTS_SYNTHESIS, "audioChunks", 1);
				tracker.markResponseEmission();
			}).doOnComplete(() -> {
				inputsMono.flatMap(inputs -> conversationCounterPort.increment())
					.filter(count -> count % conversationThreshold == 0)
					.flatMap(count -> memoryExtractionService.checkAndExtract())
					.subscribeOn(Schedulers.boundedElastic()).onErrorResume(error -> {
						log.warn("파이프라인 {}의 메모리 추출 실패: {}",
							tracker.pipelineId(),
							error.getMessage());
						return Mono.empty();
					}).subscribe();
			});

		return tracker.attachLifecycle(audioStream);
	}

	@Override
	public Flux<String> executeTextOnly(String text) {
		DialoguePipelineTracker tracker = pipelineMonitor.create(text);

		Mono<PipelineInputs> inputsMono = prepareInputs(text, tracker).cache();

		Flux<String> llmTokens = streamLlmTokens(tracker, inputsMono).subscribeOn(
			Schedulers.boundedElastic()).doOnNext(token -> {
				tracker
					.incrementStageCounter(DialoguePipelineStage.LLM_COMPLETION, "tokenCount", 1);
				log.debug("LLM Token: [{}]", token);
			});

		Flux<String> textStream = llmTokens.share();

		textStream.collectList().flatMap(tokens -> {
			String fullResponse = String.join("", tokens);

			if (llmPort instanceof TokenUsageProvider tokenUsageProvider) {
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

			return inputsMono.flatMap(inputs -> pipelineTracer.tracePersistence(tracker,
				() -> conversationRepository
					.save(inputs.currentTurn().withResponse(fullResponse))));
		}).flatMap(turn -> conversationCounterPort.increment())
			.filter(count -> count % conversationThreshold == 0)
			.flatMap(count -> memoryExtractionService.checkAndExtract())
			.subscribeOn(Schedulers.boundedElastic()).onErrorResume(error -> {
				return Mono.empty();
			}).subscribe();

		return tracker.attachLifecycle(textStream);
	}

	@Override
	public Flux<String> executeStreaming(String text) {
		return executeAudioStreaming(text).map(bytes -> Base64.getEncoder().encodeToString(bytes));
	}

	// ===================================================================

	/**
	 * 대화 기록을 로드하고 ConversationContext를 반환합니다.
	 *
	 * @return ConversationContext
	 */
	private Mono<ConversationContext> loadConversationHistory() {
		return conversationRepository.findRecent(10).collectList().map(ConversationContext::of)
			.defaultIfEmpty(ConversationContext.empty());
	}

	/**
	 * 파이프라인 공통 입력(현재 턴, 메모리 검색, 문서 검색, 대화 히스토리)을 준비해 캐싱합니다.
	 */
	private Mono<PipelineInputs> prepareInputs(String text, DialoguePipelineTracker tracker) {
		Mono<ConversationTurn> currentTurn = Mono.fromCallable(() -> ConversationTurn.create(text))
			.cache();

		Mono<MemoryRetrievalResult> memories = pipelineTracer.traceMemories(tracker,
			() -> retrievalPort.retrieveMemories(text, 5));

		Mono<RetrievalContext> retrievalContext = pipelineTracer.traceRetrieval(tracker,
			() -> retrievalPort.retrieve(text, 3));

		Mono<ConversationContext> history = loadConversationHistory().cache();

		return Mono.zip(retrievalContext, memories, history, currentTurn)
			.map(tuple -> new PipelineInputs(tuple.getT1(), tuple.getT2(), tuple.getT3(),
				tuple.getT4()));
	}

	/**
	 * 준비된 입력을 사용해 메시지를 만들고 LLM 토큰을 스트리밍합니다.
	 */
	private Flux<String> streamLlmTokens(DialoguePipelineTracker tracker,
		Mono<PipelineInputs> inputsMono) {
		return inputsMono.flatMapMany(inputs -> pipelineTracer.tracePrompt(tracker,
			() -> buildMessages(inputs.retrievalContext(),
				inputs.memories(),
				inputs.conversationContext(),
				inputs.currentTurn().query()))
			.flatMapMany(messages -> {
				CompletionRequest request = new CompletionRequest(
					messages,
					llmModel,
					true,
					java.util.Map.of("correlationId", tracker.pipelineId()));
				return pipelineTracer.traceLlm(tracker,
					request.model(),
					() -> llmPort.streamCompletion(request));
			}));
	}

	/**
	 * 메시지를 생성하고 List<Message>를 반환합니다.
	 */
	private List<Message> buildMessages(RetrievalContext context,
		MemoryRetrievalResult memories,
		ConversationContext conversationContext,
		String currentQuery) {
		List<Message> messages = new ArrayList<>();

		String fullSystemPrompt = systemPromptService.buildSystemPrompt(context, memories);
		if (fullSystemPrompt == null || fullSystemPrompt.isBlank()) {
			fullSystemPrompt = "You are a helpful assistant.";
		}
		messages.add(Message.system(fullSystemPrompt));

		conversationContext.turns().stream().filter(turn -> turn.response() != null)
			.forEach(turn -> {
				messages.add(Message.user(turn.query()));
				messages.add(Message.assistant(turn.response()));
			});

		messages.add(Message.user(currentQuery));

		return messages;
	}

	/**
	 * 파이프라인 단계 간 공유되는 입력 모음입니다.
	 */
	private record PipelineInputs(
		RetrievalContext retrievalContext,
		MemoryRetrievalResult memories,
		ConversationContext conversationContext,
		ConversationTurn currentTurn) {
	}
}
