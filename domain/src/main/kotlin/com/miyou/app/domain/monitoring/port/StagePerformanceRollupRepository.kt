package com.miyou.app.domain.monitoring.port

import com.miyou.app.domain.monitoring.model.MetricsGranularity
import com.miyou.app.domain.monitoring.model.StagePerformanceRollup
import reactor.core.publisher.Flux
import java.time.Instant

interface StagePerformanceRollupRepository {
    fun saveAll(rollups: Flux<StagePerformanceRollup>): Flux<StagePerformanceRollup>

    fun findByGranularityAndBucketStartBetween(
        granularity: MetricsGranularity,
        startTime: Instant,
        endTime: Instant,
    ): Flux<StagePerformanceRollup>
}
