package com.miyou.app.domain.monitoring.port

import com.miyou.app.domain.monitoring.model.MetricsGranularity
import com.miyou.app.domain.monitoring.model.MetricsRollup
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface MetricsRollupRepository {
    fun save(rollup: MetricsRollup): Mono<MetricsRollup>

    fun findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
        granularity: MetricsGranularity,
        startTime: Instant,
        endTime: Instant,
    ): Flux<MetricsRollup>
}
