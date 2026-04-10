package com.miyou.app.infrastructure.outbound.monitoring

import com.miyou.app.domain.monitoring.model.DialoguePipelineStage
import com.miyou.app.domain.monitoring.model.PerformanceMetrics
import com.miyou.app.domain.monitoring.model.PipelineSummary
import com.miyou.app.domain.monitoring.model.StageSnapshot
import com.miyou.app.domain.monitoring.model.UsageAnalytics
import com.miyou.app.domain.monitoring.port.PerformanceMetricsRepository
import com.miyou.app.domain.monitoring.port.PipelineMetricsReporter
import com.miyou.app.domain.monitoring.port.UsageAnalyticsRepository
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class PersistentPipelineMetricsReporter(
    private val performanceMetricsRepository: PerformanceMetricsRepository,
    private val usageAnalyticsRepository: UsageAnalyticsRepository,
    private val loggingReporter: LoggingPipelineMetricsReporter,
) : PipelineMetricsReporter {
    private val log = LoggerFactory.getLogger(PersistentPipelineMetricsReporter::class.java)

    override fun report(summary: PipelineSummary) {
        loggingReporter.report(summary)

        subscribeWithLogging(
            savePerformanceMetrics(summary),
            "performance metrics",
            summary.pipelineId,
            PerformanceMetrics::pipelineId,
        )
        subscribeWithLogging(
            saveUsageAnalytics(summary),
            "usage analytics",
            summary.pipelineId,
            UsageAnalytics::pipelineId,
        )
    }

    private fun savePerformanceMetrics(summary: PipelineSummary): Mono<PerformanceMetrics> {
        val metrics =
            PerformanceMetrics(
                summary.pipelineId,
                summary.status.name,
                summary.startedAt,
                summary.finishedAt,
                summary.durationMillis(),
                summary.firstResponseLatencyMillis,
                summary.lastResponseLatencyMillis,
                summary.stages.map { stage ->
                    PerformanceMetrics.StagePerformance(
                        stage.stage.name,
                        stage.status.name,
                        stage.startedAt,
                        stage.finishedAt,
                        stage.durationMillis,
                        stage.attributes,
                    )
                },
                summary.attributes,
            )
        return performanceMetricsRepository.save(metrics)
    }

    private fun saveUsageAnalytics(summary: PipelineSummary): Mono<UsageAnalytics> {
        val attrs = summary.attributes

        var inputPreview = extractString(attrs, "input.preview")
        val inputText = extractString(attrs, "input.text").ifBlank { inputPreview }

        val userRequest =
            UsageAnalytics.UserRequest(
                inputText,
                extractInt(attrs, "input.length"),
                inputPreview,
            )

        val llmUsage = extractLlmUsage(summary)
        val retrievalMetrics = extractRetrievalMetrics(summary)
        val ttsMetrics = extractTtsMetrics(summary)
        val responseMetrics =
            UsageAnalytics.ResponseMetrics(
                summary.durationMillis(),
                summary.firstResponseLatencyMillis,
                summary.lastResponseLatencyMillis,
            )

        val analytics =
            UsageAnalytics
                .builder()
                .pipelineId(summary.pipelineId)
                .status(summary.status.toString())
                .timestamp(summary.finishedAt)
                .userRequest(userRequest)
                .llmUsage(llmUsage)
                .retrievalMetrics(retrievalMetrics)
                .ttsMetrics(ttsMetrics)
                .responseMetrics(responseMetrics)
                .build()

        return usageAnalyticsRepository.save(analytics)
    }

    private fun extractLlmUsage(summary: PipelineSummary): UsageAnalytics.LlmUsage? {
        val llmStage = findStage(summary, DialoguePipelineStage.LLM_COMPLETION) ?: return null
        val attrs = llmStage.attributes

        var totalTokens = extractInt(attrs, "totalTokens")
        if (totalTokens == 0) {
            totalTokens = extractInt(attrs, "tokenCount")
        }

        val promptTokens = extractIntOrNull(attrs, "promptTokens")
        val completionTokens = extractIntOrNull(attrs, "completionTokens")

        return UsageAnalytics.LlmUsage(
            extractString(attrs, "model"),
            promptTokens ?: 0,
            completionTokens ?: 0,
            totalTokens,
            summary.llmOutputs,
            llmStage.durationMillis,
        )
    }

    private fun extractRetrievalMetrics(summary: PipelineSummary): UsageAnalytics.RetrievalMetrics {
        var memoryCount = 0
        var documentCount = 0
        var retrievalTime = 0L

        summary.stages.forEach { stage ->
            when (stage.stage) {
                DialoguePipelineStage.MEMORY_RETRIEVAL -> {
                    memoryCount = extractInt(stage.attributes, "memoryCount")
                    retrievalTime += stage.durationMillis
                }

                DialoguePipelineStage.RETRIEVAL -> {
                    documentCount = extractInt(stage.attributes, "documentCount")
                    retrievalTime += stage.durationMillis
                }

                else -> {}
            }
        }

        return UsageAnalytics.RetrievalMetrics(memoryCount, documentCount, retrievalTime)
    }

    private fun extractTtsMetrics(summary: PipelineSummary): UsageAnalytics.TtsMetrics {
        var sentenceCount = 0
        var audioChunks = 0
        var synthesisTime = 0L
        var audioLengthMillis = 0L

        summary.stages.forEach { stage ->
            when (stage.stage) {
                DialoguePipelineStage.SENTENCE_ASSEMBLY -> {
                    sentenceCount = extractInt(stage.attributes, "sentenceCount")
                }

                DialoguePipelineStage.TTS_SYNTHESIS -> {
                    audioChunks = extractInt(stage.attributes, "audioChunks")
                    synthesisTime = stage.durationMillis
                    audioLengthMillis = extractLong(stage.attributes, "audioLengthMillis")
                }

                else -> {}
            }
        }

        val resolvedAudioLengthMillis = if (audioLengthMillis == 0L) synthesisTime else audioLengthMillis
        return UsageAnalytics.TtsMetrics(
            sentenceCount,
            audioChunks,
            synthesisTime,
            resolvedAudioLengthMillis,
        )
    }

    private fun findStage(
        summary: PipelineSummary,
        stage: DialoguePipelineStage,
    ): StageSnapshot? = summary.stages.firstOrNull { it.stage == stage }

    private fun <T> subscribeWithLogging(
        source: Mono<T>,
        label: String,
        pipelineId: String,
        idExtractor: (T) -> String,
    ) {
        source.subscribe(
            { result -> log.debug("{} saved: {}", label, idExtractor(result)) },
            { error -> log.error("Failed to save {} for {}", label, pipelineId, error) },
        )
    }

    private fun extractString(
        map: Map<String, Any?>,
        key: String,
    ): String = map[key]?.toString().orEmpty()

    private fun extractInt(
        map: Map<String, Any?>,
        key: String,
    ): Int {
        val value = map[key]
        return if (value is Number) value.toInt() else 0
    }

    private fun extractIntOrNull(
        map: Map<String, Any?>,
        key: String,
    ): Int? {
        val value = map[key]
        return if (value is Number) value.toInt() else null
    }

    private fun extractLong(
        map: Map<String, Any?>,
        key: String,
    ): Long {
        val value = map[key]
        return if (value is Number) value.toLong() else 0L
    }
}
