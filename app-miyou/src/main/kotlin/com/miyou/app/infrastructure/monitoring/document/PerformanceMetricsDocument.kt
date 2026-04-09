package com.miyou.app.infrastructure.monitoring.document

import com.miyou.app.domain.monitoring.model.PerformanceMetrics
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "performance_metrics")
data class PerformanceMetricsDocument(
    @Id val pipelineId: String,
    val status: String,
    @Indexed val startedAt: Instant?,
    val finishedAt: Instant?,
    @Indexed val totalDurationMillis: Long,
    val firstResponseLatencyMillis: Long?,
    val lastResponseLatencyMillis: Long?,
    val stages: List<StagePerformanceDoc>,
    val systemAttributes: Map<String, Any?>,
) {
    companion object {
        fun fromDomain(domain: PerformanceMetrics): PerformanceMetricsDocument =
            PerformanceMetricsDocument(
                pipelineId = domain.pipelineId,
                status = domain.status,
                startedAt = domain.startedAt,
                finishedAt = domain.finishedAt,
                totalDurationMillis = domain.totalDurationMillis,
                firstResponseLatencyMillis = domain.firstResponseLatencyMillis,
                lastResponseLatencyMillis = domain.lastResponseLatencyMillis,
                stages = domain.stages.map(StagePerformanceDoc::fromDomain),
                systemAttributes = sanitizeMapKeys(domain.systemAttributes),
            )
    }

    fun toDomain(): PerformanceMetrics =
        PerformanceMetrics(
            pipelineId,
            status,
            startedAt,
            finishedAt,
            totalDurationMillis,
            firstResponseLatencyMillis,
            lastResponseLatencyMillis,
            stages.map(StagePerformanceDoc::toDomain),
            restoreMapKeys(systemAttributes),
        )

    @Document
    data class StagePerformanceDoc(
        val stageName: String,
        val status: String,
        val startedAt: Instant?,
        val finishedAt: Instant?,
        val durationMillis: Long,
        val attributes: Map<String, Any?>,
    ) {
        companion object {
            fun fromDomain(domain: PerformanceMetrics.StagePerformance): StagePerformanceDoc =
                StagePerformanceDoc(
                    stageName = domain.stageName,
                    status = domain.status,
                    startedAt = domain.startedAt,
                    finishedAt = domain.finishedAt,
                    durationMillis = domain.durationMillis,
                    attributes = sanitizeMapKeys(domain.attributes),
                )
        }

        fun toDomain(): PerformanceMetrics.StagePerformance =
            PerformanceMetrics.StagePerformance(
                stageName = stageName,
                status = status,
                startedAt = startedAt,
                finishedAt = finishedAt,
                durationMillis = durationMillis,
                attributes = restoreMapKeys(attributes),
            )
    }
}

private fun sanitizeMapKeys(map: Map<String, Any?>): Map<String, Any?> =
    map.entries.associate { (key, value) ->
        encodeKey(key) to value
    }

private fun restoreMapKeys(map: Map<String, Any?>): Map<String, Any?> =
    map.entries.associate { (key, value) ->
        decodeKey(key) to value
    }

private fun encodeKey(key: String): String = key.replace("%", "%25").replace(".", "%2E")

private fun decodeKey(key: String): String = key.replace("%2E", ".").replace("%25", "%")
