package com.miyou.app.domain.monitoring.model

import com.miyou.app.domain.cost.model.CostInfo
import java.time.Instant

/**
 * 대화 파이프라인 실행의 종합 분석 데이터를 표현합니다.
 */
data class UsageAnalytics(
    val pipelineId: String,
    val status: String,
    val timestamp: Instant?,
    val userRequest: UserRequest?,
    val llmUsage: LlmUsage?,
    val retrievalMetrics: RetrievalMetrics?,
    val ttsMetrics: TtsMetrics?,
    val responseMetrics: ResponseMetrics?,
    val costInfo: CostInfo?,
) {
    /**
     * 사용자 입력 관련 지표입니다.
     */
    data class UserRequest(
        val inputText: String,
        val inputLength: Int,
        val inputPreview: String?,
    )

    /**
     * LLM 사용량과 생성 문장 지표입니다.
     */
    data class LlmUsage(
        val model: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val generatedSentences: List<String>,
        val completionTimeMillis: Long,
    )

    /**
     * RAG 검색 관련 지표입니다.
     */
    data class RetrievalMetrics(
        val memoryCount: Int,
        val documentCount: Int,
        val retrievalTimeMillis: Long,
    )

    /**
     * TTS 합성 관련 지표입니다.
     */
    data class TtsMetrics(
        val sentenceCount: Int,
        val audioChunks: Int,
        val synthesisTimeMillis: Long,
        val audioLengthMillis: Long,
    )

    /**
     * 응답 지연 및 실행 시간 지표입니다.
     */
    data class ResponseMetrics(
        val totalDurationMillis: Long,
        val firstResponseLatencyMillis: Long?,
        val lastResponseLatencyMillis: Long?,
    )

    class Builder {
        private var pipelineId: String? = null
        private var status: String? = null
        private var timestamp: Instant? = null
        private var userRequest: UserRequest? = null
        private var llmUsage: LlmUsage? = null
        private var retrievalMetrics: RetrievalMetrics? = null
        private var ttsMetrics: TtsMetrics? = null
        private var responseMetrics: ResponseMetrics? = null
        private var costInfo: CostInfo? = null

        fun pipelineId(pipelineId: String?): Builder = apply { this.pipelineId = pipelineId }

        fun status(status: String?): Builder = apply { this.status = status }

        fun timestamp(timestamp: Instant?): Builder = apply { this.timestamp = timestamp }

        fun userRequest(userRequest: UserRequest?): Builder = apply { this.userRequest = userRequest }

        fun llmUsage(llmUsage: LlmUsage?): Builder = apply { this.llmUsage = llmUsage }

        fun retrievalMetrics(retrievalMetrics: RetrievalMetrics?): Builder =
            apply {
                this.retrievalMetrics =
                    retrievalMetrics
            }

        fun ttsMetrics(ttsMetrics: TtsMetrics?): Builder = apply { this.ttsMetrics = ttsMetrics }

        fun responseMetrics(responseMetrics: ResponseMetrics?): Builder =
            apply { this.responseMetrics = responseMetrics }

        fun costInfo(costInfo: CostInfo?): Builder = apply { this.costInfo = costInfo }

        fun build(): UsageAnalytics =
            UsageAnalytics(
                pipelineId = pipelineId.orEmpty(),
                status = status.orEmpty(),
                timestamp = timestamp,
                userRequest = userRequest,
                llmUsage = llmUsage,
                retrievalMetrics = retrievalMetrics,
                ttsMetrics = ttsMetrics,
                responseMetrics = responseMetrics,
                costInfo = costInfo,
            )
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
