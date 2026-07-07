package com.miyou.app.monitoring.repository

import com.miyou.app.monitoring.document.StagePerformanceRollupDocument
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import java.time.Instant

interface SpringDataStagePerformanceRollupRepository :
    ReactiveMongoRepository<StagePerformanceRollupDocument, String> {
    fun findByGranularityAndBucketStartBetween(
        granularity: String,
        startTime: Instant,
        endTime: Instant,
    ): Flux<StagePerformanceRollupDocument>
}
