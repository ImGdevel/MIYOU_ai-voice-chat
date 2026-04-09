package com.miyou.app.application.monitoring.service

import com.miyou.app.application.monitoring.context.PipelineContext
import com.miyou.app.application.monitoring.monitor.DialoguePipelineTracker
import com.miyou.app.domain.dialogue.model.Message
import com.miyou.app.domain.dialogue.model.MessageRole
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.monitoring.model.DialoguePipelineStage
import com.miyou.app.domain.retrieval.model.RetrievalContext
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.BiConsumer
import java.util.function.Supplier

@Component
class PipelineTracer {
    fun traceMemories(supplier: Supplier<Mono<MemoryRetrievalResult>>): Mono<MemoryRetrievalResult> {
        return Mono.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            if (tracker == null) {
                return@deferContextual supplier.get().cache()
            }
            tracker
                .traceMono(DialoguePipelineStage.MEMORY_RETRIEVAL, supplier)
                .doOnNext { result ->
                    tracker.recordStageAttribute(
                        DialoguePipelineStage.MEMORY_RETRIEVAL,
                        "memoryCount",
                        result.totalCount(),
                    )
                    val memoryContents =
                        result.allMemories().map { memory ->
                            "[${memory.type}] ${memory.content}"
                        }
                    if (memoryContents.isNotEmpty()) {
                        tracker.recordStageAttribute(
                            DialoguePipelineStage.MEMORY_RETRIEVAL,
                            "memories",
                            memoryContents,
                        )
                    }
                }.cache()
        }
    }

    fun traceRetrieval(supplier: Supplier<Mono<RetrievalContext>>): Mono<RetrievalContext> {
        return Mono.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            if (tracker == null) {
                return@deferContextual supplier.get().cache()
            }
            tracker
                .traceMono(DialoguePipelineStage.RETRIEVAL, supplier)
                .doOnNext { context ->
                    tracker.recordStageAttribute(
                        DialoguePipelineStage.RETRIEVAL,
                        "documentCount",
                        context.documentCount(),
                    )
                    if (!context.isEmpty()) {
                        val docContents = context.documents.map { it.content }
                        tracker.recordStageAttribute(
                            DialoguePipelineStage.RETRIEVAL,
                            "documents",
                            docContents,
                        )
                    }
                }.cache()
        }
    }

    fun tracePrompt(builder: Supplier<List<Message>>): Mono<List<Message>> {
        return Mono.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            if (tracker == null) {
                return@deferContextual Mono.fromCallable(builder::get)
            }
            tracker
                .traceMono(DialoguePipelineStage.PROMPT_BUILDING) { Mono.fromCallable(builder::get) }
                .doOnNext { messages ->
                    val systemPrompt = messages.firstOrNull { it.role == MessageRole.SYSTEM }?.content.orEmpty()
                    tracker.recordStageAttribute(DialoguePipelineStage.PROMPT_BUILDING, "systemPrompt", systemPrompt)
                    tracker.recordStageAttribute(DialoguePipelineStage.PROMPT_BUILDING, "messageCount", messages.size)
                }
        }
    }

    fun <T> traceLlm(
        model: String,
        supplier: Supplier<Flux<T>>,
    ): Flux<T> {
        return Flux.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            if (tracker == null) {
                return@deferContextual supplier.get()
            }
            tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION, "model", model)
            return@deferContextual tracker.traceFlux(DialoguePipelineStage.LLM_COMPLETION, supplier)
        }
    }

    fun traceTtsPreparation(supplier: Supplier<Mono<Void>>): Mono<Void> {
        return Mono.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            if (tracker == null) {
                return@deferContextual supplier.get()
            }
            tracker.traceMono(DialoguePipelineStage.TTS_PREPARATION, supplier)
        }
    }

    fun <T> traceSentenceAssembly(
        supplier: Supplier<Flux<T>>,
        recorder: BiConsumer<DialoguePipelineTracker, T>,
    ): Flux<T> {
        return Flux.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            if (tracker == null) {
                return@deferContextual supplier.get()
            }
            tracker
                .traceFlux(DialoguePipelineStage.SENTENCE_ASSEMBLY, supplier)
                .doOnNext { item ->
                    tracker.incrementStageCounter(DialoguePipelineStage.SENTENCE_ASSEMBLY, "sentenceCount", 1)
                    recorder.accept(tracker, item)
                }
        }
    }

    fun <T> traceTtsSynthesis(
        supplier: Supplier<Flux<T>>,
        onNext: BiConsumer<DialoguePipelineTracker, T>,
    ): Flux<T> {
        return Flux.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            if (tracker == null) {
                return@deferContextual supplier.get()
            }
            tracker
                .traceFlux(DialoguePipelineStage.TTS_SYNTHESIS, supplier)
                .doOnNext { item -> onNext.accept(tracker, item) }
        }
    }

    fun <T> tracePersistence(supplier: Supplier<Mono<T>>): Mono<T> {
        return Mono.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            if (tracker == null) {
                return@deferContextual supplier.get()
            }
            tracker.traceMono(DialoguePipelineStage.QUERY_PERSISTENCE, supplier)
        }
    }

    fun <T> incrementOnNext(
        source: Flux<T>,
        stage: DialoguePipelineStage,
        key: String,
        delta: Int,
    ): Flux<T> {
        return source.doOnEach { signal ->
            if (!signal.isOnNext) {
                return@doOnEach
            }
            val tracker = PipelineContext.findTracker(signal.contextView)
            if (tracker != null) {
                tracker.incrementStageCounter(stage, key, delta.toLong())
            }
        }
    }
}
