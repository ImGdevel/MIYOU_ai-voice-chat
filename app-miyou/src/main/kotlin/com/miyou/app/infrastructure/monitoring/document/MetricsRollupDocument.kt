package com.miyou.app.infrastructure.monitoring.document

import com.miyou.app.domain.monitoring.model.MetricsGranularity
import com.miyou.app.domain.monitoring.model.MetricsRollup
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "metrics_rollups")
data class MetricsRollupDocument(
    @Id val id: String?,
    @Indexed val bucketStart: Instant,
    @Indexed val granularity: String,
    val requestCount: Long,
    val totalTokens: Long,
    val totalDurationMillis: Long,
    val avgResponseMillis: Double,
) {
    companion object {
        fun fromDomain(domain: MetricsRollup): MetricsRollupDocument {
            val id = "${domain.granularity.name}-${domain.bucketStart.toEpochMilli()}"
            return MetricsRollupDocument(
                id,
                domain.bucketStart,
                domain.granularity.name,
                domain.requestCount,
                domain.totalTokens,
                domain.totalDurationMillis,
                domain.avgResponseMillis,
            )
        }
    }

    fun toDomain(): MetricsRollup =
        MetricsRollup(
            bucketStart,
            MetricsGranularity.valueOf(granularity),
            requestCount,
            totalTokens,
            totalDurationMillis,
            avgResponseMillis,
        )
}
