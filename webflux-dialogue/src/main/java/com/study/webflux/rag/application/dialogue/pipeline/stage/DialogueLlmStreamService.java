package com.study.webflux.rag.application.dialogue.pipeline.stage;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.dialogue.pipeline.PipelineInputs;
import com.study.webflux.rag.application.monitoring.context.PipelineContext;
import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueMessageService;
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

	/**
	 * 준비된 입력을 사용해 LLM 토큰 스트림을 생성합니다.
	 */
	public Flux<String> buildLlmTokenStream(Mono<PipelineInputs> inputsMono) {
		return streamLlmTokens(inputsMono)
			.subscribeOn(Schedulers.boundedElastic())
			.transform(this::trackLlmTokens);
	}

	private Flux<String> streamLlmTokens(Mono<PipelineInputs> inputsMono) {
		return Flux.deferContextual(contextView -> {
			var tracker = PipelineContext.findTracker(contextView);
			var metadata = tracker != null
				? java.util.Map.<String, Object>of("correlationId", tracker.pipelineId())
				: java.util.Map.<String, Object>of();
			return inputsMono.flatMapMany(inputs -> pipelineTracer.tracePrompt(
				() -> messageService.buildMessages(inputs.retrievalContext(),
					inputs.memories(),
					inputs.conversationContext(),
					inputs.currentTurn().query()))
				.flatMapMany(messages -> {
					CompletionRequest request = new CompletionRequest(
						messages,
						llmModel,
						true,
						metadata);
					return pipelineTracer.traceLlm(request.model(),
						() -> llmPort.streamCompletion(request));
				}));
		});
	}

	private Flux<String> trackLlmTokens(Flux<String> llmTokens) {
		return pipelineTracer.incrementOnNext(llmTokens,
			DialoguePipelineStage.LLM_COMPLETION,
			"tokenCount",
			1).doOnNext(token -> log.debug("LLM Token: [{}]", token));
	}
}
