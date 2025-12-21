package com.study.webflux.rag.application.service;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.application.monitoring.DialoguePipelineStage;
import com.study.webflux.rag.application.monitoring.DialoguePipelineTracker;
import com.study.webflux.rag.domain.model.llm.Message;
import com.study.webflux.rag.domain.model.llm.MessageRole;
import com.study.webflux.rag.domain.model.memory.MemoryRetrievalResult;
import com.study.webflux.rag.domain.model.rag.RetrievalContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class PipelineTracer {

	public Mono<MemoryRetrievalResult> traceMemories(DialoguePipelineTracker tracker,
		Supplier<Mono<MemoryRetrievalResult>> supplier) {
		return tracker.traceMono(DialoguePipelineStage.MEMORY_RETRIEVAL, supplier)
			.doOnNext(result -> {
				tracker.recordStageAttribute(DialoguePipelineStage.MEMORY_RETRIEVAL,
					"memoryCount",
					result.totalCount());
				List<String> memoryContents = result.allMemories().stream()
					.map(m -> String.format("[%s] %s", m.type(), m.content()))
					.toList();
				if (!memoryContents.isEmpty()) {
					tracker.recordStageAttribute(DialoguePipelineStage.MEMORY_RETRIEVAL,
						"memories",
						memoryContents);
				}
			}).cache();
	}

	public Mono<RetrievalContext> traceRetrieval(DialoguePipelineTracker tracker,
		Supplier<Mono<RetrievalContext>> supplier) {
		return tracker.traceMono(DialoguePipelineStage.RETRIEVAL, supplier).doOnNext(context -> {
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
		}).cache();
	}

	public Mono<List<Message>> tracePrompt(DialoguePipelineTracker tracker,
		Supplier<List<Message>> builder) {
		return tracker.traceMono(DialoguePipelineStage.PROMPT_BUILDING,
			() -> Mono.fromCallable(builder::get)).doOnNext(messages -> {
				String systemPrompt = messages.stream()
					.filter(m -> MessageRole.SYSTEM.equals(m.role()))
					.findFirst().map(m -> m.content()).orElse("");
				tracker.recordStageAttribute(DialoguePipelineStage.PROMPT_BUILDING,
					"systemPrompt",
					systemPrompt);
				tracker.recordStageAttribute(DialoguePipelineStage.PROMPT_BUILDING,
					"messageCount",
					messages.size());
			});
	}

	public <T> Flux<T> traceLlm(DialoguePipelineTracker tracker,
		String model,
		Supplier<Flux<T>> supplier) {
		tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION, "model", model);
		return tracker.traceFlux(DialoguePipelineStage.LLM_COMPLETION, supplier);
	}
}
