package com.miyou.app.monitoring.micrometer

import com.miyou.app.monitoring.config.CostTrackingMetricsConfiguration
import com.miyou.app.monitoring.config.LlmMetricsConfiguration
import com.miyou.app.monitoring.config.UxMetricsConfiguration
import com.miyou.app.monitoring.model.DialoguePipelineStage
import com.miyou.app.monitoring.model.PipelineStatus
import com.miyou.app.monitoring.model.PipelineSummary
import com.miyou.app.monitoring.model.StageSnapshot
import com.miyou.app.monitoring.model.StageStatus
import com.miyou.app.monitoring.port.PipelineMetricsReporter
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Micrometer 기반 파이프라인 메트릭 리포터.
 */
@Component
class MicrometerPipelineMetricsReporter(
    private val meterRegistry: MeterRegistry,
    private val llmMetrics: LlmMetricsConfiguration,
    private val costMetrics: CostTrackingMetricsConfiguration,
    private val uxMetrics: UxMetricsConfiguration,
) : PipelineMetricsReporter {
    override fun report(summary: PipelineSummary) {
        recordPipelineMetrics(summary)
        recordStageMetrics(summary)
        recordStageGapMetrics(summary)
        recordLlmMetrics(summary)
        recordResponseLatencyMetrics(summary)
        recordCostMetrics(summary)
        recordUxMetrics(summary)
    }

    private fun recordPipelineMetrics(summary: PipelineSummary) {
        val status = summary.status.name.lowercase()

        Timer
            .builder(METRIC_PREFIX + ".duration")
            .tag("status", status)
            .description("Total pipeline execution time")
            .register(meterRegistry)
            .record(summary.durationMillis(), TimeUnit.MILLISECONDS)

        Counter
            .builder(METRIC_PREFIX + ".executions")
            .tag("status", status)
            .description("Number of pipeline executions")
            .register(meterRegistry)
            .increment()

        val inputLength = summary.attributes["input.length"]
        if (inputLength is Number) {
            meterRegistry
                .summary(METRIC_PREFIX + ".input.length")
                .record(inputLength.toDouble())
        }
    }

    private fun recordStageMetrics(summary: PipelineSummary) {
        summary.stages.forEach { stage ->
            val stageName = stage.stage.name.lowercase()
            val stageStatus = stage.status.name.lowercase()

            if (stage.durationMillis >= 0) {
                Timer
                    .builder(METRIC_PREFIX + ".stage.duration")
                    .tag("stage", stageName)
                    .tag("status", stageStatus)
                    .description("Stage execution time")
                    .register(meterRegistry)
                    .record(stage.durationMillis, TimeUnit.MILLISECONDS)
            }

            recordStageAttributes(stage)
        }
    }

    private fun recordStageAttributes(stage: StageSnapshot) {
        val stageName = stage.stage.name.lowercase()
        when (stage.stage) {
            DialoguePipelineStage.MEMORY_RETRIEVAL -> {
                recordCounterFromAttribute(
                    stage,
                    "memoryCount",
                    METRIC_PREFIX + ".memory.retrieved",
                    stageName,
                )
                recordRagQualityMetrics(stage)
            }

            DialoguePipelineStage.RETRIEVAL -> {
                recordCounterFromAttribute(
                    stage,
                    "documentCount",
                    METRIC_PREFIX + ".documents.retrieved",
                    stageName,
                )
            }

            DialoguePipelineStage.SENTENCE_ASSEMBLY -> {
                recordCounterFromAttribute(
                    stage,
                    "sentenceCount",
                    METRIC_PREFIX + ".sentences.generated",
                    stageName,
                )
                recordCounterFromAttribute(
                    stage,
                    "sentenceChars",
                    METRIC_PREFIX + ".sentences.chars",
                    stageName,
                )
            }

            DialoguePipelineStage.TTS_SYNTHESIS -> {
                recordCounterFromAttribute(
                    stage,
                    "audioChunks",
                    METRIC_PREFIX + ".audio.chunks",
                    stageName,
                )
                recordCounterFromAttribute(
                    stage,
                    "audioBytes",
                    METRIC_PREFIX + ".audio.bytes",
                    stageName,
                )
            }

            else -> {}
        }
    }

    private fun recordRagQualityMetrics(stage: StageSnapshot) {
        val experientialCount = stage.attributes["memory.experiential.count"]
        if (experientialCount is Number) {
            meterRegistry.gauge(
                "rag.memory.count",
                Tags.of("memory_type", "experiential"),
                experientialCount.toDouble(),
            )
        }

        val factualCount = stage.attributes["memory.factual.count"]
        if (factualCount is Number) {
            meterRegistry.gauge(
                "rag.memory.count",
                Tags.of("memory_type", "factual"),
                factualCount.toDouble(),
            )
        }
    }

    private fun recordCounterFromAttribute(
        stage: StageSnapshot,
        attrKey: String,
        metricName: String,
        @Suppress("UNUSED_PARAMETER") stageName: String,
    ) {
        val value = stage.attributes[attrKey]
        if (value is Number) {
            meterRegistry
                .summary(metricName)
                .record(value.toDouble())
        }
    }

    private fun recordLlmMetrics(summary: PipelineSummary) {
        summary.stages
            .filter { it.stage == DialoguePipelineStage.LLM_COMPLETION }
            .forEach { stage ->
                val model = stage.attributes["model"]
                val modelTag = model?.toString() ?: "unknown"

                llmMetrics.recordLlmRequest()

                when (stage.status) {
                    StageStatus.COMPLETED -> {
                        llmMetrics.recordLlmSuccess(modelTag)
                    }

                    StageStatus.FAILED -> {
                        val error = stage.attributes["error"]
                        val errorType = error?.toString() ?: "unknown"
                        llmMetrics.recordLlmFailure(modelTag, errorType)
                    }

                    else -> {}
                }

                recordTokenCounter(stage, "promptTokens", "prompt", modelTag)
                recordTokenCounter(stage, "completionTokens", "completion", modelTag)
                recordTokenCounter(stage, "totalTokens", "total", modelTag)

                val promptTokens = stage.attributes["promptTokens"]
                if (promptTokens is Number) {
                    llmMetrics.recordPromptLength(promptTokens.toInt())
                }

                val completionTokens = stage.attributes["completionTokens"]
                if (completionTokens is Number) {
                    llmMetrics.recordCompletionLength(completionTokens.toInt())
                }

                if (stage.durationMillis >= 0) {
                    llmMetrics.recordResponseTime(stage.durationMillis)
                    llmMetrics.recordResponseTimeByModel(modelTag, stage.durationMillis)
                }

                val outputChars = summary.llmOutputs.sumOf { it.length }
                meterRegistry
                    .summary(METRIC_PREFIX + ".llm.output.chars")
                    .record(outputChars.toDouble())
            }
    }

    private fun recordTokenCounter(
        stage: StageSnapshot,
        attrKey: String,
        tokenType: String,
        model: String,
    ) {
        val value = stage.attributes[attrKey]
        if (value is Number) {
            Counter
                .builder("llm.tokens")
                .tag("type", tokenType)
                .tag("model", model)
                .description("LLM token usage")
                .register(meterRegistry)
                .increment(value.toDouble())
        }
    }

    private fun recordResponseLatencyMetrics(summary: PipelineSummary) {
        summary.firstTokenLatencyMillis?.let { firstTokenLatency ->
            Timer
                .builder(METRIC_PREFIX + ".response.first_token")
                .description("TTFT - time from request to first LLM token")
                .register(meterRegistry)
                .record(firstTokenLatency, TimeUnit.MILLISECONDS)
        }

        summary.firstResponseLatencyMillis?.let { firstLatency ->
            Timer
                .builder(METRIC_PREFIX + ".response.first")
                .description("Time to first response")
                .register(meterRegistry)
                .record(firstLatency, TimeUnit.MILLISECONDS)
        }

        summary.lastResponseLatencyMillis?.let { lastLatency ->
            Timer
                .builder(METRIC_PREFIX + ".response.last")
                .description("Time to last response")
                .register(meterRegistry)
                .record(lastLatency, TimeUnit.MILLISECONDS)
        }
    }

    private fun recordStageGapMetrics(summary: PipelineSummary) {
        val stages =
            summary.stages.sortedWith { a, b ->
                val startedAtA = a.startedAt
                val startedAtB = b.startedAt
                when {
                    startedAtA == null -> 1
                    startedAtB == null -> -1
                    else -> startedAtA.compareTo(startedAtB)
                }
            }

        for (i in 0 until stages.size - 1) {
            val current = stages[i]
            val next = stages[i + 1]
            if (current.finishedAt != null && next.startedAt != null) {
                val gapMillis = Duration.between(current.finishedAt, next.startedAt).toMillis()
                if (gapMillis >= 0) {
                    Timer
                        .builder(METRIC_PREFIX + ".stage.gap")
                        .tag("from_stage", current.stage.name.lowercase())
                        .tag("to_stage", next.stage.name.lowercase())
                        .description("Time gap between stage transitions")
                        .register(meterRegistry)
                        .record(gapMillis, TimeUnit.MILLISECONDS)
                }
            }
        }
    }

    private fun recordCostMetrics(summary: PipelineSummary) {
        val sentenceChars =
            findStage(
                summary,
                DialoguePipelineStage.SENTENCE_ASSEMBLY
            )?.attributes?.get("sentenceChars")

        summary.stages.forEach { stage ->
            when (stage.stage) {
                DialoguePipelineStage.LLM_COMPLETION -> {
                    val model = stage.attributes["model"]?.toString() ?: "unknown"
                    val promptTokens = stage.attributes["promptTokens"]
                    val completionTokens = stage.attributes["completionTokens"]

                    if (promptTokens is Number && completionTokens is Number) {
                        val promptTokenValue = promptTokens.toInt()
                        val completionTokenValue = completionTokens.toInt()
                        costMetrics.recordLlmCost(model, promptTokenValue, completionTokenValue)

                        if (!summary.userId.isNullOrBlank()) {
                            val cost = calculateLlmCost(model, promptTokenValue, completionTokenValue)
                            costMetrics.recordUserLlmCost(summary.userId, model, cost)
                        }
                    }
                }

                DialoguePipelineStage.TTS_SYNTHESIS -> {
                    if (sentenceChars is Number) {
                        costMetrics.recordTtsCost(TTS_PROVIDER, sentenceChars.toInt())
                    }
                }

                else -> {}
            }
        }
    }

    private fun findStage(
        summary: PipelineSummary,
        stage: DialoguePipelineStage,
    ): StageSnapshot? = summary.stages.firstOrNull { it.stage == stage }

    private fun recordUxMetrics(summary: PipelineSummary) {
        summary.firstResponseLatencyMillis
            ?.takeIf { it >= 0 }
            ?.let { uxMetrics.recordFirstResponseLatency(it) }

        val totalDurationMs = summary.durationMillis()
        if (totalDurationMs >= 0) {
            uxMetrics.recordCompleteResponseLatency(totalDurationMs)
        }

        if (summary.status == PipelineStatus.FAILED) {
            val errorType = summary.attributes["error"]?.toString() ?: "unknown"
            uxMetrics.recordError(errorType)
        }
    }

    private fun calculateLlmCost(
        model: String,
        promptTokens: Int,
        completionTokens: Int,
    ): Double {
        val modelLower = model.lowercase()
        return when {
            modelLower.contains("gpt-4o") && !modelLower.contains("mini") -> {
                (promptTokens / 1_000_000.0) * 2.50 + (completionTokens / 1_000_000.0) * 10.00
            }

            modelLower.contains("gpt-4o-mini") -> {
                (promptTokens / 1_000_000.0) * 0.150 + (completionTokens / 1_000_000.0) * 0.600
            }

            modelLower.contains("gpt-4-turbo") -> {
                (promptTokens / 1_000_000.0) * 10.00 + (completionTokens / 1_000_000.0) * 30.00
            }

            modelLower.contains("gpt-3.5-turbo") -> {
                (promptTokens / 1_000_000.0) * 0.50 + (completionTokens / 1_000_000.0) * 1.50
            }

            else -> {
                ((promptTokens + completionTokens) / 1_000_000.0) * 5.0
            }
        }
    }

    companion object {
        private const val METRIC_PREFIX = "dialogue.pipeline"

        // 현재 이 코드베이스에 연결된 TTS 공급자가 Supertone 하나뿐이라 고정값으로 태깅한다.
        // 두 번째 공급자가 추가되면 그때 어댑터에서 실제 provider 이름을 전달받도록 바꾼다.
        private const val TTS_PROVIDER = "supertone"
    }
}
