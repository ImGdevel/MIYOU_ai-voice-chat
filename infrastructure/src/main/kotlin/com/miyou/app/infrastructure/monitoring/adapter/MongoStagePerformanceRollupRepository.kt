package com.miyou.app.infrastructure.monitoring.adapter

import com.miyou.app.domain.monitoring.model.MetricsGranularity
import com.miyou.app.domain.monitoring.model.StagePerformanceRollup
import com.miyou.app.domain.monitoring.port.StagePerformanceRollupRepository
import com.miyou.app.infrastructure.monitoring.document.StagePerformanceRollupDocument
import com.miyou.app.infrastructure.monitoring.repository.SpringDataStagePerformanceRollupRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.Instant

@Repository
class MongoStagePerformanceRollupRepository(
    private val repository: SpringDataStagePerformanceRollupRepository,
) : StagePerformanceRollupRepository {
    override fun saveAll(rollups: Flux<StagePerformanceRollup>): Flux<StagePerformanceRollup> =
        repository
            .saveAll(rollups.map(StagePerformanceRollupDocument::fromDomain))
            .map(StagePerformanceRollupDocument::toDomain)

    override fun findByGranularityAndBucketStartBetween(
        granularity: MetricsGranularity,
        startTime: Instant,
        endTime: Instant,
    ): Flux<StagePerformanceRollup> =
        repository
            .findByGranularityAndBucketStartBetween(
                granularity.name,
                startTime,
                endTime,
            ).map(StagePerformanceRollupDocument::toDomain)
}
