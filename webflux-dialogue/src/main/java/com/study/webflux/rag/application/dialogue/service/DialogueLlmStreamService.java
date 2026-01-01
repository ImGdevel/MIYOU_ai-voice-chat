package com.study.webflux.rag.application.dialogue.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineTracker;
import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.model.CompletionRequest;
import com.study.webflux.rag.domain.dialogue.port.LlmPort;
import com.study.webflux.rag.domain.monitoring.model.DialoguePipelineStage;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
public class DialogueLlmStreamService {

	private final LlmPort llmPort;
	private final PipelineTracer pipelineTracer;
	private final DialogueMessageService messageService;
	private final String llmModel;

	public DialogueLlmStreamService(LlmPort llmPort,
		PipelineTracer pipelineTracer,
		DialogueMessageService messageService,
		RagDialogueProperties properties) {
		this.llmPort = llmPort;
		this.pipelineTracer = pipelineTracer;
		this.messageService = messageService;
		this.llmModel = properties.getOpenai().getModel();
	}

	public Flux<String> buildLlmTokenStream(DialoguePipelineTracker tracker,
		Mono<PipelineInputs> inputsMono) {
		return streamLlmTokens(tracker, inputsMono)
			.subscribeOn(Schedulers.boundedElastic())
			.transform(tokens -> trackLlmTokens(tracker, tokens));
	}

	private Flux<String> streamLlmTokens(DialoguePipelineTracker tracker,
		Mono<PipelineInputs> inputsMono) {
		return inputsMono.flatMapMany(inputs -> pipelineTracer.tracePrompt(tracker,
			() -> messageService.buildMessages(inputs.retrievalContext(),
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

	private Flux<String> trackLlmTokens(DialoguePipelineTracker tracker, Flux<String> llmTokens) {
		return llmTokens.doOnNext(token -> {
			pipelineTracer.increment(tracker,
				DialoguePipelineStage.LLM_COMPLETION,
				"tokenCount",
				1);
			log.debug("LLM Token: [{}]", token);
		});
	}
}
