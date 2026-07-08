package com.miyou.app.application.monitoring.service

import com.miyou.app.domain.dialogue.model.Message
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.monitoring.context.PipelineContext
import com.miyou.app.monitoring.model.DialoguePipelineStage
import com.miyou.app.monitoring.monitor.DialoguePipelineTracker
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.BiConsumer
import java.util.function.Supplier

@Component
class PipelineTracer {
    private fun <T> traceIfPresent(
        stage: DialoguePipelineStage,
        supplier: Supplier<Mono<T>>,
        fallback: Supplier<Mono<T>> = supplier,
        onTrackerPresent: (DialoguePipelineTracker, Mono<T>) -> Mono<T> = { _, mono -> mono },
    ): Mono<T> =
        Mono.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            if (tracker == null) {
                fallback.get()
            } else {
                onTrackerPresent(tracker, tracker.traceMono(stage, supplier))
            }
        }

    private fun <T> traceIfPresent(
        stage: DialoguePipelineStage,
        supplier: Supplier<Flux<T>>,
        fallback: Supplier<Flux<T>> = supplier,
        onTrackerPresent: (DialoguePipelineTracker, Flux<T>) -> Flux<T> = { _, flux -> flux },
    ): Flux<T> =
        Flux.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            if (tracker == null) {
                fallback.get()
            } else {
                onTrackerPresent(tracker, tracker.traceFlux(stage, supplier))
            }
        }

    fun traceMemories(supplier: Supplier<Mono<MemoryRetrievalResult>>): Mono<MemoryRetrievalResult> =
        traceIfPresent(
            stage = DialoguePipelineStage.MEMORY_RETRIEVAL,
            supplier = supplier,
            fallback = { supplier.get().cache() },
            onTrackerPresent = { tracker, mono ->
                mono
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
        )

    fun traceRetrieval(supplier: Supplier<Mono<RetrievalContext>>): Mono<RetrievalContext> =
        traceIfPresent(
            stage = DialoguePipelineStage.RETRIEVAL,
            supplier = supplier,
            fallback = { supplier.get().cache() },
            onTrackerPresent = { tracker, mono ->
                mono
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
        )

    fun tracePrompt(builder: Supplier<List<Message>>): Mono<List<Message>> =
        traceIfPresent(
            stage = DialoguePipelineStage.PROMPT_BUILDING,
            supplier = Supplier { Mono.fromCallable(builder::get) },
            onTrackerPresent = { tracker, mono ->
                mono.doOnNext { messages ->
                    // 시스템 프롬프트 전문은 기록하지 않는다 - 정적 페르소나 템플릿 부분은
                    // 파이프라인 레벨 personaId로 식별 가능하고, 동적 부분(장기기억/검색문서)은
                    // 이미 traceMemories/traceRetrieval이 별도로 기록하므로 중복이다.
                    tracker.recordStageAttribute(DialoguePipelineStage.PROMPT_BUILDING, "messageCount", messages.size)
                }
            }
        )

    fun <T> traceLlm(
        model: String,
        supplier: Supplier<Flux<T>>,
    ): Flux<T> =
        traceIfPresent(
            stage = DialoguePipelineStage.LLM_COMPLETION,
            supplier = supplier,
            onTrackerPresent = { tracker, flux ->
                tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION, "model", model)
                flux
            }
        )

    fun traceTtsPreparation(supplier: Supplier<Mono<Void>>): Mono<Void> =
        traceIfPresent(DialoguePipelineStage.TTS_PREPARATION, supplier)

    fun <T> traceSentenceAssembly(
        supplier: Supplier<Flux<T>>,
        recorder: BiConsumer<DialoguePipelineTracker, T>,
    ): Flux<T> =
        traceIfPresent(
            stage = DialoguePipelineStage.SENTENCE_ASSEMBLY,
            supplier = supplier,
            onTrackerPresent = { tracker, flux ->
                flux.doOnNext { item ->
                    tracker.incrementStageCounter(DialoguePipelineStage.SENTENCE_ASSEMBLY, "sentenceCount", 1)
                    recorder.accept(tracker, item)
                }
            }
        )

    fun <T> traceTtsSynthesis(
        supplier: Supplier<Flux<T>>,
        onNext: BiConsumer<DialoguePipelineTracker, T>,
    ): Flux<T> =
        traceIfPresent(
            stage = DialoguePipelineStage.TTS_SYNTHESIS,
            supplier = supplier,
            onTrackerPresent = { tracker, flux ->
                flux.doOnNext { item -> onNext.accept(tracker, item) }
            }
        )

    fun <T> tracePersistence(supplier: Supplier<Mono<T>>): Mono<T> =
        traceIfPresent(DialoguePipelineStage.QUERY_PERSISTENCE, supplier)

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
