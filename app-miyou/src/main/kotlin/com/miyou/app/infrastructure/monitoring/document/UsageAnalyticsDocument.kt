package com.miyou.app.infrastructure.monitoring.document

import com.miyou.app.domain.cost.model.CostInfo
import com.miyou.app.domain.monitoring.model.UsageAnalytics
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "usage_analytics")
@CompoundIndex(name = "timestamp_model", def = "{'timestamp': -1, 'llmUsage.model': 1}")
data class UsageAnalyticsDocument(
    @Id val pipelineId: String,
    val status: String,
    @Indexed val timestamp: Instant?,
    val userRequest: UserRequestDoc?,
    val llmUsage: LlmUsageDoc?,
    val retrievalMetrics: RetrievalMetricsDoc?,
    val ttsMetrics: TtsMetricsDoc?,
    val responseMetrics: ResponseMetricsDoc?,
    val costInfo: CostInfoDoc?,
) {
    data class UserRequestDoc(
        val inputText: String?,
        val inputLength: Int,
        val inputPreview: String?,
    ) {
        companion object {
            fun fromDomain(domain: UsageAnalytics.UserRequest): UserRequestDoc =
                UserRequestDoc(domain.inputText, domain.inputLength, domain.inputPreview)
        }

        fun toDomain(): UsageAnalytics.UserRequest =
            UsageAnalytics.UserRequest(inputText.orEmpty(), inputLength, inputPreview)
    }

    data class LlmUsageDoc(
        @Indexed val model: String?,
        val promptTokens: Int,
        val completionTokens: Int,
        @Indexed val totalTokens: Int,
        val generatedSentences: List<String>,
        val completionTimeMillis: Long,
    ) {
        companion object {
            fun fromDomain(domain: UsageAnalytics.LlmUsage): LlmUsageDoc =
                LlmUsageDoc(
                    domain.model,
                    domain.promptTokens,
                    domain.completionTokens,
                    domain.totalTokens,
                    domain.generatedSentences,
                    domain.completionTimeMillis,
                )
        }

        fun toDomain(): UsageAnalytics.LlmUsage =
            UsageAnalytics.LlmUsage(
                model.orEmpty(),
                promptTokens,
                completionTokens,
                totalTokens,
                generatedSentences,
                completionTimeMillis,
            )
    }

    data class RetrievalMetricsDoc(
        val memoryCount: Int,
        val documentCount: Int,
        val retrievalTimeMillis: Long,
    ) {
        companion object {
            fun fromDomain(domain: UsageAnalytics.RetrievalMetrics): RetrievalMetricsDoc =
                RetrievalMetricsDoc(
                    domain.memoryCount,
                    domain.documentCount,
                    domain.retrievalTimeMillis,
                )
        }

        fun toDomain(): UsageAnalytics.RetrievalMetrics =
            UsageAnalytics.RetrievalMetrics(
                memoryCount,
                documentCount,
                retrievalTimeMillis,
            )
    }

    data class TtsMetricsDoc(
        val sentenceCount: Int,
        val audioChunks: Int,
        val synthesisTimeMillis: Long,
        val audioLengthMillis: Long,
    ) {
        companion object {
            fun fromDomain(domain: UsageAnalytics.TtsMetrics): TtsMetricsDoc =
                TtsMetricsDoc(
                    domain.sentenceCount,
                    domain.audioChunks,
                    domain.synthesisTimeMillis,
                    domain.audioLengthMillis,
                )
        }

        fun toDomain(): UsageAnalytics.TtsMetrics =
            UsageAnalytics.TtsMetrics(sentenceCount, audioChunks, synthesisTimeMillis, audioLengthMillis)
    }

    data class ResponseMetricsDoc(
        val totalDurationMillis: Long,
        val firstResponseLatencyMillis: Long?,
        val lastResponseLatencyMillis: Long?,
    ) {
        companion object {
            fun fromDomain(domain: UsageAnalytics.ResponseMetrics): ResponseMetricsDoc =
                ResponseMetricsDoc(
                    domain.totalDurationMillis,
                    domain.firstResponseLatencyMillis,
                    domain.lastResponseLatencyMillis,
                )
        }

        fun toDomain(): UsageAnalytics.ResponseMetrics =
            UsageAnalytics.ResponseMetrics(
                totalDurationMillis,
                firstResponseLatencyMillis,
                lastResponseLatencyMillis,
            )
    }

    data class CostInfoDoc(
        val llmCredits: Long,
        val ttsCredits: Long,
        val totalCredits: Long,
    ) {
        companion object {
            fun fromDomain(domain: CostInfo): CostInfoDoc =
                CostInfoDoc(domain.llmCredits, domain.ttsCredits, domain.totalCredits)
        }

        fun toDomain(): CostInfo = CostInfo(llmCredits, ttsCredits, totalCredits)
    }

    companion object {
        fun fromDomain(domain: UsageAnalytics): UsageAnalyticsDocument =
            UsageAnalyticsDocument(
                domain.pipelineId,
                domain.status,
                domain.timestamp,
                domain.userRequest?.let(UserRequestDoc::fromDomain),
                domain.llmUsage?.let(LlmUsageDoc::fromDomain),
                domain.retrievalMetrics?.let(RetrievalMetricsDoc::fromDomain),
                domain.ttsMetrics?.let(TtsMetricsDoc::fromDomain),
                domain.responseMetrics?.let(ResponseMetricsDoc::fromDomain),
                domain.costInfo?.let(CostInfoDoc::fromDomain),
            )
    }

    fun toDomain(): UsageAnalytics =
        UsageAnalytics(
            pipelineId,
            status,
            timestamp,
            userRequest?.toDomain(),
            llmUsage?.toDomain(),
            retrievalMetrics?.toDomain(),
            ttsMetrics?.toDomain(),
            responseMetrics?.toDomain(),
            costInfo?.toDomain(),
        )
}
