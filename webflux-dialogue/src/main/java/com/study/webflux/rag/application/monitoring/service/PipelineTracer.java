package com.study.webflux.rag.application.monitoring.service;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.application.monitoring.context.PipelineContext;
import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineTracker;
import com.study.webflux.rag.domain.dialogue.model.Message;
import com.study.webflux.rag.domain.dialogue.model.MessageRole;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.monitoring.model.DialoguePipelineStage;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class PipelineTracer {

	public Mono<MemoryRetrievalResult> traceMemories(
		Supplier<Mono<MemoryRetrievalResult>> supplier) {
		return Mono.deferContextual(contextView -> {
			DialoguePipelineTracker tracker = PipelineContext.findTracker(contextView);
			if (tracker == null) {
				return supplier.get().cache();
			}
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
		});
	}

	public Mono<RetrievalContext> traceRetrieval(Supplier<Mono<RetrievalContext>> supplier) {
		return Mono.deferContextual(contextView -> {
			DialoguePipelineTracker tracker = PipelineContext.findTracker(contextView);
			if (tracker == null) {
				return supplier.get().cache();
			}
			return tracker.traceMono(DialoguePipelineStage.RETRIEVAL, supplier)
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
				}).cache();
		});
	}

	public Mono<List<Message>> tracePrompt(Supplier<List<Message>> builder) {
		return Mono.deferContextual(contextView -> {
			DialoguePipelineTracker tracker = PipelineContext.findTracker(contextView);
			if (tracker == null) {
				return Mono.fromCallable(builder::get);
			}
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
		});
	}

	public <T> Flux<T> traceLlm(String model, Supplier<Flux<T>> supplier) {
		return Flux.deferContextual(contextView -> {
			DialoguePipelineTracker tracker = PipelineContext.findTracker(contextView);
			if (tracker == null) {
				return supplier.get();
			}
			tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION, "model", model);
			return tracker.traceFlux(DialoguePipelineStage.LLM_COMPLETION, supplier);
		});
	}

	public Mono<Void> traceTtsPreparation(Supplier<Mono<Void>> supplier) {
		return Mono.deferContextual(contextView -> {
			DialoguePipelineTracker tracker = PipelineContext.findTracker(contextView);
			if (tracker == null) {
				return supplier.get();
			}
			return tracker.traceMono(DialoguePipelineStage.TTS_PREPARATION, supplier);
		});
	}

	public <T> Flux<T> traceSentenceAssembly(Supplier<Flux<T>> supplier,
		BiConsumer<DialoguePipelineTracker, T> recorder) {
		return Flux.deferContextual(contextView -> {
			DialoguePipelineTracker tracker = PipelineContext.findTracker(contextView);
			if (tracker == null) {
				return supplier.get();
			}
			return tracker.traceFlux(DialoguePipelineStage.SENTENCE_ASSEMBLY, supplier)
				.doOnNext(item -> {
					tracker.incrementStageCounter(DialoguePipelineStage.SENTENCE_ASSEMBLY,
						"sentenceCount",
						1);
					recorder.accept(tracker, item);
				});
		});
	}

	public <T> Flux<T> traceTtsSynthesis(Supplier<Flux<T>> supplier,
		BiConsumer<DialoguePipelineTracker, T> onNext) {
		return Flux.deferContextual(contextView -> {
			DialoguePipelineTracker tracker = PipelineContext.findTracker(contextView);
			if (tracker == null) {
				return supplier.get();
			}
			return tracker.traceFlux(DialoguePipelineStage.TTS_SYNTHESIS, supplier)
				.doOnNext(chunk -> onNext.accept(tracker, chunk));
		});
	}

	public <T> Mono<T> tracePersistence(Supplier<Mono<T>> supplier) {
		return Mono.deferContextual(contextView -> {
			DialoguePipelineTracker tracker = PipelineContext.findTracker(contextView);
			if (tracker == null) {
				return supplier.get();
			}
			return tracker.traceMono(DialoguePipelineStage.QUERY_PERSISTENCE, supplier);
		});
	}

	public <T> Flux<T> incrementOnNext(Flux<T> source,
		DialoguePipelineStage stage,
		String key,
		int delta) {
		return source.doOnEach(signal -> {
			if (!signal.isOnNext()) {
				return;
			}
			DialoguePipelineTracker tracker = PipelineContext.findTracker(signal.getContextView());
			if (tracker != null) {
				tracker.incrementStageCounter(stage, key, delta);
			}
		});
	}
}
