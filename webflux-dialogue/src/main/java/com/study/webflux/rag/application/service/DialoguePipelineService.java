package com.study.webflux.rag.application.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.monitoring.DialoguePipelineMonitor;
import com.study.webflux.rag.application.monitoring.DialoguePipelineStage;
import com.study.webflux.rag.application.monitoring.DialoguePipelineTracker;
import com.study.webflux.rag.domain.model.conversation.ConversationContext;
import com.study.webflux.rag.domain.model.conversation.ConversationTurn;
import com.study.webflux.rag.domain.model.llm.CompletionRequest;
import com.study.webflux.rag.domain.model.llm.Message;
import com.study.webflux.rag.domain.model.llm.MessageRole;
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
import com.study.webflux.rag.infrastructure.template.FileBasedPromptTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
public class DialoguePipelineService implements DialoguePipelineUseCase {

	private final LlmPort llmPort;
	private final TtsPort ttsPort;
	private final RetrievalPort retrievalPort;
	private final ConversationRepository conversationRepository;
	private final SentenceAssembler sentenceAssembler;
	private final DialoguePipelineMonitor pipelineMonitor;
	private final ConversationCounterPort conversationCounterPort;
	private final MemoryExtractionService memoryExtractionService;
	private final FileBasedPromptTemplate promptTemplate;

	private final int conversationThreshold; // 대화 횟수 임계값
	private final String configuredSystemPrompt; // 추가 시스템 프롬프트
	private final String configuredSystemPromptTemplate; // 추가 시스템 프롬프트 템플릿
	private final String cachedSystemPromptFromTemplate; // 템플릿 내용 캐싱

	public DialoguePipelineService(LlmPort llmPort,
		TtsPort ttsPort,
		RetrievalPort retrievalPort,
		ConversationRepository conversationRepository,
		SentenceAssembler sentenceAssembler,
		DialoguePipelineMonitor pipelineMonitor,
		ConversationCounterPort conversationCounterPort,
		MemoryExtractionService memoryExtractionService,
		int conversationThreshold,
		RagDialogueProperties ragDialogueProperties,
		FileBasedPromptTemplate promptTemplate) {
		this.llmPort = llmPort;
		this.ttsPort = ttsPort;
		this.retrievalPort = retrievalPort;
		this.conversationRepository = conversationRepository;
		this.sentenceAssembler = sentenceAssembler;
		this.pipelineMonitor = pipelineMonitor;
		this.conversationCounterPort = conversationCounterPort;
		this.memoryExtractionService = memoryExtractionService;
		this.promptTemplate = promptTemplate;
		this.conversationThreshold = conversationThreshold;
		this.configuredSystemPrompt = ragDialogueProperties.getSystemPrompt();
		this.configuredSystemPromptTemplate = ragDialogueProperties.getSystemPromptTemplate();
		this.cachedSystemPromptFromTemplate = loadSystemPromptTemplate();
	}

	/**
	 * 대화 파이프라인을 실행하고 텍스트 스트림을 반환합니다.
	 *
	 * @param text
	 *            대화 텍스트
	 * @return 텍스트 스트림
	 */
	@Override
	public Flux<String> executeStreaming(String text) {
		return executeAudioStreaming(text).map(bytes -> Base64.getEncoder().encodeToString(bytes));
	}

	@Override
	public Flux<String> executeTextOnly(String text) {
		DialoguePipelineTracker tracker = pipelineMonitor.create(text);

		Mono<ConversationTurn> queryTurn = Mono.fromCallable(() -> ConversationTurn.create(text))
			.cache();

		Mono<MemoryRetrievalResult> memoryResult = tracker
			.traceMono(DialoguePipelineStage.MEMORY_RETRIEVAL,
				() -> retrievalPort.retrieveMemories(text, 5))
			.doOnNext(result -> {
				tracker.recordStageAttribute(DialoguePipelineStage.MEMORY_RETRIEVAL,
					"memoryCount",
					result.totalCount());
				List<String> memoryContents = new ArrayList<>();
				result.experientialMemories()
					.forEach(m -> memoryContents.add("[경험] " + m.content()));
				result.factualMemories().forEach(m -> memoryContents.add("[사실] " + m.content()));
				if (!memoryContents.isEmpty()) {
					tracker.recordStageAttribute(DialoguePipelineStage.MEMORY_RETRIEVAL,
						"memories",
						memoryContents);
				}
			});

		Mono<RetrievalContext> retrievalContext = tracker
			.traceMono(DialoguePipelineStage.RETRIEVAL, () -> retrievalPort.retrieve(text, 3))
			.doOnNext(context -> {
				tracker.recordStageAttribute(DialoguePipelineStage.RETRIEVAL,
					"documentCount",
					context.documentCount());
				if (!context.isEmpty()) {
					List<String> docContents = context.documents().stream()
						.map(doc -> doc.content())
						.collect(Collectors.toList());
					tracker.recordStageAttribute(DialoguePipelineStage.RETRIEVAL,
						"documents",
						docContents);
				}
			});

		Flux<String> llmTokens = Mono
			.zip(retrievalContext, memoryResult, loadConversationHistory(), queryTurn)
			.flatMapMany(tuple -> {
				RetrievalContext context = tuple.getT1();
				MemoryRetrievalResult memories = tuple.getT2();
				ConversationContext conversationContext = tuple.getT3();
				ConversationTurn currentTurn = tuple.getT4();

				return tracker.traceMono(DialoguePipelineStage.PROMPT_BUILDING,
					() -> Mono.fromCallable(() -> buildMessages(context,
						memories,
						conversationContext,
						currentTurn.query())))
					.doOnNext(messages -> {
						String systemPrompt = messages.stream()
							.filter(m -> MessageRole.SYSTEM.equals(m.role()))
							.findFirst().map(m -> m.content()).orElse("");
						tracker.recordStageAttribute(DialoguePipelineStage.PROMPT_BUILDING,
							"systemPrompt",
							systemPrompt);
						tracker.recordStageAttribute(DialoguePipelineStage.PROMPT_BUILDING,
							"messageCount",
							messages.size());
					}).flatMapMany(messages -> {
						CompletionRequest request = new CompletionRequest(
							messages,
							"gpt-4o-mini",
							true,
							java.util.Map.of("correlationId", tracker.pipelineId()));
						tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION,
							"model",
							request.model());
						return tracker.traceFlux(DialoguePipelineStage.LLM_COMPLETION,
							() -> llmPort.streamCompletion(request));
					});
			}).subscribeOn(Schedulers.boundedElastic()).doOnNext(token -> {
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

			return queryTurn.flatMap(turn -> tracker.traceMono(
				DialoguePipelineStage.QUERY_PERSISTENCE,
				() -> conversationRepository.save(turn.withResponse(fullResponse))));
		}).flatMap(turn -> conversationCounterPort.increment())
			.filter(count -> count % conversationThreshold == 0)
			.flatMap(count -> memoryExtractionService.checkAndExtract())
			.subscribeOn(Schedulers.boundedElastic()).onErrorResume(error -> {
				log.warn("파이프라인 {}의 메모리 추출 실패: {}", tracker.pipelineId(), error.getMessage());
				return Mono.empty();
			}).subscribe();

		return tracker.attachLifecycle(textStream);
	}

	/**
	 * 대화 파이프라인을 실행하고 오디오 스트림을 반환합니다.
	 *
	 * @param text
	 *            대화 텍스트
	 * @return 오디오 스트림
	 */
	@Override
	public Flux<byte[]> executeAudioStreaming(String text) {
		DialoguePipelineTracker tracker = pipelineMonitor.create(text);

		// TTS 준비
		Mono<Void> ttsWarmup = tracker.traceMono(DialoguePipelineStage.TTS_PREPARATION,
			() -> ttsPort.prepare()
				.doOnError(error -> log
					.warn("파이프라인 {}의 TTS 준비 실패: {}", tracker.pipelineId(), error.getMessage()))
				.onErrorResume(error -> Mono.empty()))
			.cache();

		ttsWarmup.subscribe();

		/// 쿼리 준비 (저장은 응답 생성 후 수행)
		Mono<ConversationTurn> queryTurn = Mono.fromCallable(() -> ConversationTurn.create(text))
			.cache();

		/// 메모리 검색
		Mono<MemoryRetrievalResult> memoryResult = tracker
			.traceMono(DialoguePipelineStage.MEMORY_RETRIEVAL,
				() -> retrievalPort.retrieveMemories(text, 5))
			.doOnNext(result -> {
				tracker.recordStageAttribute(DialoguePipelineStage.MEMORY_RETRIEVAL,
					"memoryCount",
					result.totalCount());
				List<String> memoryContents = new ArrayList<>();
				result.experientialMemories()
					.forEach(m -> memoryContents.add("[경험] " + m.content()));
				result.factualMemories().forEach(m -> memoryContents.add("[사실] " + m.content()));
				if (!memoryContents.isEmpty()) {
					tracker.recordStageAttribute(DialoguePipelineStage.MEMORY_RETRIEVAL,
						"memories",
						memoryContents);
				}
			});

		/// 검색 컨텍스트 로드
		Mono<RetrievalContext> retrievalContext = tracker
			.traceMono(DialoguePipelineStage.RETRIEVAL, () -> retrievalPort.retrieve(text, 3))
			.doOnNext(context -> {
				tracker.recordStageAttribute(DialoguePipelineStage.RETRIEVAL,
					"documentCount",
					context.documentCount());
				if (!context.isEmpty()) {
					List<String> docContents = context.documents().stream()
						.map(doc -> doc.content())
						.collect(Collectors.toList());
					tracker.recordStageAttribute(DialoguePipelineStage.RETRIEVAL,
						"documents",
						docContents);
				}
			});

		/// LLM 토큰 생성
		Flux<String> llmTokens = Mono
			.zip(retrievalContext, memoryResult, loadConversationHistory(), queryTurn)
			.flatMapMany(tuple -> {
				RetrievalContext context = tuple.getT1();
				MemoryRetrievalResult memories = tuple.getT2();
				ConversationContext conversationContext = tuple.getT3();
				ConversationTurn currentTurn = tuple.getT4();

				return tracker.traceMono(DialoguePipelineStage.PROMPT_BUILDING,
					() -> Mono.fromCallable(() -> buildMessages(context,
						memories,
						conversationContext,
						currentTurn.query())))
					.doOnNext(messages -> {
						String systemPrompt = messages.stream()
							.filter(m -> MessageRole.SYSTEM.equals(m.role()))
							.findFirst().map(m -> m.content()).orElse("");
						tracker.recordStageAttribute(DialoguePipelineStage.PROMPT_BUILDING,
							"systemPrompt",
							systemPrompt);
						tracker.recordStageAttribute(DialoguePipelineStage.PROMPT_BUILDING,
							"messageCount",
							messages.size());
					}).flatMapMany(messages -> {
						CompletionRequest request = CompletionRequest
							.withMessages(messages, "gpt-4o-mini", true);
						tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION,
							"model",
							request.model());
						return tracker.traceFlux(DialoguePipelineStage.LLM_COMPLETION,
							() -> llmPort.streamCompletion(request));
					});
			}).subscribeOn(Schedulers.boundedElastic()).doOnNext(token -> tracker
				.incrementStageCounter(DialoguePipelineStage.LLM_COMPLETION, "tokenCount", 1));

		/// 문장 어셈블
		Flux<String> sentences = tracker.traceFlux(DialoguePipelineStage.SENTENCE_ASSEMBLY,
			() -> sentenceAssembler.assemble(llmTokens)).doOnNext(sentence -> {
				tracker.incrementStageCounter(DialoguePipelineStage.SENTENCE_ASSEMBLY,
					"sentenceCount",
					1);
				tracker.recordLlmOutput(sentence);
				log.debug("Sentence: [{}]", sentence);
			}).share();

		/// 오디오 스트림 생성
		Flux<byte[]> audioFlux = sentences.publish(sharedSentences -> {
			Flux<String> cachedSentences = sharedSentences.replay().autoConnect(2);

			cachedSentences.collectList().flatMap(sentenceList -> {
				String fullResponse = String.join(" ", sentenceList);
				return queryTurn.flatMap(turn -> tracker.traceMono(
					DialoguePipelineStage.QUERY_PERSISTENCE,
					() -> conversationRepository.save(turn.withResponse(fullResponse))));
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

		/// 오디오 스트림 추적
		Flux<byte[]> audioStream = tracker
			.traceFlux(DialoguePipelineStage.TTS_SYNTHESIS, () -> audioFlux).doOnNext(chunk -> {
				tracker
					.incrementStageCounter(DialoguePipelineStage.TTS_SYNTHESIS, "audioChunks", 1);
				tracker.markResponseEmission();
			}).doOnComplete(() -> {
				queryTurn.flatMap(turn -> conversationCounterPort.increment())
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
	 * 메시지를 생성하고 List<Message>를 반환합니다.
	 */
	private List<Message> buildMessages(RetrievalContext context,
		MemoryRetrievalResult memories,
		ConversationContext conversationContext,
		String currentQuery) {
		List<Message> messages = new ArrayList<>();

		String fullSystemPrompt = buildSystemPrompt(context, memories);
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
	 * 시스템 프롬프트를 생성하고 String을 반환합니다.
	 */
	private String buildSystemPrompt(RetrievalContext context, MemoryRetrievalResult memories) {
		StringBuilder prompt = new StringBuilder();

		String staticPrompt = chooseStaticSystemPrompt();
		if (!staticPrompt.isBlank()) {
			prompt.append(staticPrompt).append("\n\n");
		}

		prompt.append("자연스럽게 대화하세요. 과도한 존댓말이나 '도와드리겠습니다' 같은 틀에 박힌 표현은 피하세요.\n\n");

		if (!memories.isEmpty()) {
			prompt.append("대화 상대에 대한 기억:\n");

			if (!memories.experientialMemories().isEmpty()) {
				prompt.append("\n경험적 기억:\n");
				memories.experientialMemories()
					.forEach(m -> prompt.append("- ").append(m.content()).append("\n"));
			}

			if (!memories.factualMemories().isEmpty()) {
				prompt.append("\n사실 기반 기억:\n");
				memories.factualMemories()
					.forEach(m -> prompt.append("- ").append(m.content()).append("\n"));
			}

			prompt.append("\n");
		}

		if (!context.isEmpty()) {
			String contextText = context.documents().stream().map(doc -> doc.content())
				.collect(Collectors.joining("\n"));
			prompt.append("참고 정보:\n").append(contextText).append("\n\n");
		}

		return prompt.toString();
	}

	private String chooseStaticSystemPrompt() {
		if (!cachedSystemPromptFromTemplate.isBlank()) {
			return cachedSystemPromptFromTemplate;
		}
		if (configuredSystemPrompt != null) {
			return configuredSystemPrompt.trim();
		}
		return "";
	}

	private String loadSystemPromptTemplate() {
		if (configuredSystemPromptTemplate == null
			|| configuredSystemPromptTemplate.isBlank()) {
			return "";
		}
		try {
			return promptTemplate.load(configuredSystemPromptTemplate).trim();
		} catch (RuntimeException e) {
			log.warn("시스템 프롬프트 템플릿 '{}'을 불러오지 못했습니다: {}",
				configuredSystemPromptTemplate,
				e.getMessage());
			return "";
		}
	}
}
