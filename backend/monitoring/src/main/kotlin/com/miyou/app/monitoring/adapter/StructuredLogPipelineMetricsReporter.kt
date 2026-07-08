package com.miyou.app.monitoring.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import com.miyou.app.monitoring.model.DialoguePipelineStage
import com.miyou.app.monitoring.model.PipelineSummary
import com.miyou.app.monitoring.model.StageSnapshot
import com.miyou.app.monitoring.port.PipelineMetricsReporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 파이프라인 실행 1건당 JSON 한 줄을 전용 로거([TRACE_LOGGER_NAME])로 남긴다.
 * 이 로거는 logback-spring.xml에서 별도 롤링 파일로만 라우팅되고(additivity=false)
 * 앱의 일반 로그와는 섞이지 않는다 - 문제 발생 시 pipelineId로 grep해서 확인하는 용도.
 * Mongo에 무한 적재하던 performance_metrics/usage_analytics를 대체한다 - 기간별
 * 집계/비용/트래픽 같은 숫자 지표는 Micrometer(Prometheus/Grafana) 쪽이 전담한다.
 */
@Component
class StructuredLogPipelineMetricsReporter(
    private val objectMapper: ObjectMapper,
) : PipelineMetricsReporter {
    private val traceLogger = LoggerFactory.getLogger(TRACE_LOGGER_NAME)

    override fun report(summary: PipelineSummary) {
        val record = buildTraceRecord(summary)
        traceLogger.info(objectMapper.writeValueAsString(record))
    }

    private fun buildTraceRecord(summary: PipelineSummary): Map<String, Any?> {
        val attrs = summary.attributes
        return linkedMapOf(
            "ts" to (summary.finishedAt ?: summary.startedAt)?.toString(),
            "pipelineId" to summary.pipelineId,
            "sessionId" to summary.sessionId,
            "userId" to summary.userId,
            "personaId" to summary.personaId,
            "status" to summary.status.name,
            "totalDurationMs" to summary.durationMillis(),
            "firstResponseMs" to summary.firstResponseLatencyMillis,
            "lastResponseMs" to summary.lastResponseLatencyMillis,
            "input" to
                mapOf(
                    "length" to extractInt(attrs, "input.length"),
                    "preview" to extractString(attrs, "input.preview"),
                ),
            "rag" to buildRag(summary),
            "llm" to buildLlm(summary),
            "tts" to buildTts(summary),
            "stages" to
                summary.stages.map { stage ->
                    mapOf(
                        "name" to stage.stage.name,
                        "status" to stage.status.name,
                        "ms" to stage.durationMillis,
                    )
                },
            "error" to attrs["error"]?.toString(),
        )
    }

    private fun buildRag(summary: PipelineSummary): Map<String, Any?> {
        val memoryAttrs = findStage(summary, DialoguePipelineStage.MEMORY_RETRIEVAL)?.attributes.orEmpty()
        val retrievalAttrs = findStage(summary, DialoguePipelineStage.RETRIEVAL)?.attributes.orEmpty()
        return mapOf(
            "memoryCount" to extractInt(memoryAttrs, "memoryCount"),
            "memories" to (memoryAttrs["memories"] as? List<*> ?: emptyList<Any?>()),
            "documentCount" to extractInt(retrievalAttrs, "documentCount"),
            "documents" to (retrievalAttrs["documents"] as? List<*> ?: emptyList<Any?>()),
        )
    }

    private fun buildLlm(summary: PipelineSummary): Map<String, Any?> {
        val attrs = findStage(summary, DialoguePipelineStage.LLM_COMPLETION)?.attributes.orEmpty()
        var totalTokens = extractInt(attrs, "totalTokens")
        if (totalTokens == 0) {
            totalTokens = extractInt(attrs, "tokenCount")
        }
        return mapOf(
            "model" to extractString(attrs, "model"),
            "promptTokens" to extractInt(attrs, "promptTokens"),
            "completionTokens" to extractInt(attrs, "completionTokens"),
            "totalTokens" to totalTokens,
            "outputChars" to summary.llmOutputs.sumOf { it.length },
            "output" to summary.llmOutputs,
        )
    }

    private fun buildTts(summary: PipelineSummary): Map<String, Any?> {
        val sentenceAttrs = findStage(summary, DialoguePipelineStage.SENTENCE_ASSEMBLY)?.attributes.orEmpty()
        val ttsStage = findStage(summary, DialoguePipelineStage.TTS_SYNTHESIS)
        return mapOf(
            "sentenceCount" to extractInt(sentenceAttrs, "sentenceCount"),
            "audioChunks" to extractInt(ttsStage?.attributes.orEmpty(), "audioChunks"),
            "synthesisMs" to (ttsStage?.durationMillis ?: 0L),
        )
    }

    private fun findStage(
        summary: PipelineSummary,
        stage: DialoguePipelineStage,
    ): StageSnapshot? = summary.stages.firstOrNull { it.stage == stage }

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

    companion object {
        const val TRACE_LOGGER_NAME = "com.miyou.app.monitoring.tracelog"
    }
}
