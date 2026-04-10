package com.miyou.app.infrastructure.monitoring.repository

import com.miyou.app.infrastructure.monitoring.document.MetricsRollupDocument
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import java.time.Instant

interface SpringDataMetricsRollupRepository : ReactiveMongoRepository<MetricsRollupDocument, String> {
    fun findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
        granularity: String,
        startTime: Instant,
        endTime: Instant,
    ): Flux<MetricsRollupDocument>
}
