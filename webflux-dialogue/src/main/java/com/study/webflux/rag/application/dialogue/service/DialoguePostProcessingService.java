package com.study.webflux.rag.application.dialogue.service;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.memory.service.MemoryExtractionService;
import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineTracker;
import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.dialogue.port.LlmPort;
import com.study.webflux.rag.domain.dialogue.port.TokenUsageProvider;
import com.study.webflux.rag.domain.memory.port.ConversationCounterPort;
import com.study.webflux.rag.domain.monitoring.model.DialoguePipelineStage;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
	}

	public Mono<Void> persistAndExtract(Mono<PipelineInputs> inputsMono,
		Flux<String> sentences,
		DialoguePipelineTracker tracker) {
		return sentences.collectList().flatMap(sentenceList -> {
			String fullResponse = String.join(" ", sentenceList);
			return inputsMono.flatMap(inputs -> pipelineTracer.tracePersistence(tracker,
				() -> conversationRepository
					.save(inputs.currentTurn().withResponse(fullResponse))));
		}).flatMap(turn -> conversationCounterPort.increment())
			.filter(count -> count % conversationThreshold == 0)
			.flatMap(count -> memoryExtractionService.checkAndExtract())
			.subscribeOn(Schedulers.boundedElastic()).then();
	}

	public Mono<Void> persistAndExtractText(Mono<PipelineInputs> inputsMono,
		Flux<String> textStream,
		DialoguePipelineTracker tracker) {
		return textStream.collectList().flatMap(tokens -> {
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
			.subscribeOn(Schedulers.boundedElastic()).then();
	}
}
