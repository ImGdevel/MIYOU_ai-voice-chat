package com.miyou.app.application.monitoring.monitor

import com.miyou.app.domain.monitoring.model.DialoguePipelineStage
import com.miyou.app.domain.monitoring.model.PipelineStatus
import com.miyou.app.domain.monitoring.model.PipelineSummary
import com.miyou.app.domain.monitoring.model.StageSnapshot
import com.miyou.app.domain.monitoring.model.StageStatus
import com.miyou.app.domain.monitoring.port.PipelineMetricsReporter
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

class DialoguePipelineTracker(
    inputText: String?,
    private val reporter: PipelineMetricsReporter,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(DialoguePipelineTracker::class.java)
    private val pipelineId: String =
        java.util.UUID
            .randomUUID()
            .toString()
    private val startedAt: Instant = clock.instant()
    private val stageMetrics: MutableMap<DialoguePipelineStage, StageMetric> =
        EnumMap(DialoguePipelineStage::class.java)
    private val attributes: MutableMap<String, Any> = ConcurrentHashMap()
    private val finished: AtomicBoolean = AtomicBoolean(false)
    private val llmOutputs: MutableList<String> = CopyOnWriteArrayList()
    private val firstResponseAt: AtomicReference<Instant> = AtomicReference()
    private val lastResponseAt: AtomicReference<Instant> = AtomicReference()

    @Volatile
    private var finishedAt: Instant? = null

    init {
        recordPipelineAttribute("input.length", inputText?.length ?: 0)
        recordPipelineAttribute("input.preview", preview(inputText ?: ""))
    }

    fun <T> traceMono(
        stage: DialoguePipelineStage,
        supplier: Supplier<Mono<T>>,
    ): Mono<T> {
        val metric = stageMetric(stage)
        return Mono.defer {
            metric.start(clock.instant())
            val publisher = supplier.get()
            publisher
                .doOnSuccess { metric.complete(clock.instant()) }
                .doOnError { error -> metric.fail(clock.instant(), error) }
                .doOnCancel { metric.cancel(clock.instant()) }
        }
    }

    fun <T> traceFlux(
        stage: DialoguePipelineStage,
        supplier: Supplier<Flux<T>>,
    ): Flux<T> {
        val metric = stageMetric(stage)
        return Flux.defer {
            metric.start(clock.instant())
            val publisher = supplier.get()
            publisher
                .doOnComplete { metric.complete(clock.instant()) }
                .doOnError { error -> metric.fail(clock.instant(), error) }
                .doOnCancel { metric.cancel(clock.instant()) }
        }
    }

    fun <T> attachLifecycle(publisher: Flux<T>): Flux<T> =
        publisher
            .doOnComplete { finish(PipelineStatus.COMPLETED, null) }
            .doOnError { error -> finish(PipelineStatus.FAILED, error) }
            .doOnCancel { finish(PipelineStatus.CANCELLED, null) }

    fun <T> attachLifecycle(publisher: Mono<T>): Mono<T> =
        publisher
            .doOnSuccess { finish(PipelineStatus.COMPLETED, null) }
            .doOnError { error -> finish(PipelineStatus.FAILED, error) }
            .doOnCancel { finish(PipelineStatus.CANCELLED, null) }

    fun recordPipelineAttribute(
        key: String?,
        value: Any?,
    ) {
        if (!key.isNullOrBlank() && value != null) {
            attributes[key] = value
        }
    }

    fun recordStageAttribute(
        stage: DialoguePipelineStage,
        key: String?,
        value: Any?,
    ) {
        if (!key.isNullOrBlank() && value != null) {
            stageMetric(stage).putAttribute(key, value)
        }
    }

    fun incrementStageCounter(
        stage: DialoguePipelineStage,
        key: String,
        delta: Long,
    ) {
        if (key.isNotBlank()) {
            stageMetric(stage).incrementAttribute(key, delta)
        }
    }

    fun recordLlmOutput(sentence: String?) {
        if (!sentence.isNullOrBlank() && llmOutputs.size < 20) {
            llmOutputs.add(sentence)
        }
    }

    fun markResponseEmission() {
        val now = clock.instant()
        firstResponseAt.compareAndSet(null, now)
        lastResponseAt.set(now)
    }

    fun pipelineId(): String = pipelineId

    private fun stageMetric(stage: DialoguePipelineStage): StageMetric =
        stageMetrics[stage] ?: kotlin.run {
            val metric = StageMetric(stage)
            stageMetrics[stage] = metric
            metric
        }

    @Synchronized
    private fun finish(
        status: PipelineStatus,
        error: Throwable?,
    ) {
        if (finished.compareAndSet(false, true)) {
            if (error != null) {
                recordPipelineAttribute("error", error.message)
            }
            finishedAt = clock.instant()

            val summary =
                PipelineSummary(
                    pipelineId,
                    status,
                    startedAt,
                    finishedAt,
                    attributes.toMap(),
                    stageMetrics.values
                        .map { it.snapshot() }
                        .toList(),
                    llmOutputs.toList(),
                    latencyFromStart(firstResponseAt.get()),
                    latencyFromStart(lastResponseAt.get()),
                )
            reporter.report(summary)
        }
    }

    private fun latencyFromStart(instant: Instant?): Long? = instant?.let { Duration.between(startedAt, it).toMillis() }

    private fun preview(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= 80) {
            trimmed
        } else {
            trimmed.substring(0, 77) + "..."
        }
    }

    private class StageMetric(
        private val stage: DialoguePipelineStage,
    ) {
        private val attributes: MutableMap<String, Any> = ConcurrentHashMap()

        @Volatile
        private var status: StageStatus = StageStatus.PENDING

        @Volatile
        private var startedAt: Instant? = null

        @Volatile
        private var finishedAt: Instant? = null

        @Synchronized
        fun start(instant: Instant) {
            if (status == StageStatus.PENDING) {
                status = StageStatus.RUNNING
                startedAt = instant
            }
        }

        @Synchronized
        fun complete(instant: Instant) {
            if (status == StageStatus.RUNNING) {
                status = StageStatus.COMPLETED
                finishedAt = instant
            }
        }

        @Synchronized
        fun fail(
            instant: Instant,
            error: Throwable?,
        ) {
            if (status == StageStatus.RUNNING || status == StageStatus.PENDING) {
                status = StageStatus.FAILED
                finishedAt = instant
                if (error != null) {
                    attributes.putIfAbsent("error", error.message ?: "unknown")
                }
            }
        }

        @Synchronized
        fun cancel(instant: Instant) {
            if (status == StageStatus.RUNNING) {
                status = StageStatus.CANCELLED
                finishedAt = instant
            }
        }

        fun putAttribute(
            key: String,
            value: Any,
        ) {
            attributes[key] = value
        }

        fun incrementAttribute(
            key: String,
            delta: Long,
        ) {
            attributes.compute(key) { _, existing ->
                val current =
                    if (existing is Number) {
                        existing.toLong()
                    } else {
                        0L
                    }
                current + delta
            }
        }

        fun snapshot(): StageSnapshot {
            val duration =
                if (startedAt != null && finishedAt != null) {
                    Duration.between(startedAt, finishedAt).toMillis()
                } else {
                    -1L
                }
            return StageSnapshot(stage, status, startedAt, finishedAt, duration, attributes.toMap())
        }
    }
}
