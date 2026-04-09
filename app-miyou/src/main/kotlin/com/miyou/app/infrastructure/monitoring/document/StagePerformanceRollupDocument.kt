package com.miyou.app.infrastructure.monitoring.document

import com.miyou.app.domain.monitoring.model.MetricsGranularity
import com.miyou.app.domain.monitoring.model.StagePerformanceRollup
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "stage_performance_rollups")
@CompoundIndex(name = "granularity_bucket_stage", def = "{'granularity': 1, 'bucketStart': 1, 'stageName': 1}")
data class StagePerformanceRollupDocument(
    @Id val id: String?,
    @Indexed val bucketStart: Instant,
    @Indexed val granularity: String,
    val stageName: String,
    val count: Long,
    val totalDurationMillis: Long,
    val avgDurationMillis: Double,
) {
    companion object {
        fun fromDomain(domain: StagePerformanceRollup): StagePerformanceRollupDocument {
            val id =
                "${domain.granularity.name}-${domain.bucketStart.toEpochMilli()}-${domain.stageName}"
            return StagePerformanceRollupDocument(
                id,
                domain.bucketStart,
                domain.granularity.name,
                domain.stageName,
                domain.count,
                domain.totalDurationMillis,
                domain.avgDurationMillis,
            )
        }
    }

    fun toDomain(): StagePerformanceRollup =
        StagePerformanceRollup(
            bucketStart,
            MetricsGranularity.valueOf(granularity),
            stageName,
            count,
            totalDurationMillis,
            avgDurationMillis,
        )
}
