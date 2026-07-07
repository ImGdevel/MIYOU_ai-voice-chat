package com.miyou.app.monitoring.adapter

import com.miyou.app.monitoring.document.MetricsRollupDocument
import com.miyou.app.monitoring.model.MetricsGranularity
import com.miyou.app.monitoring.model.MetricsRollup
import com.miyou.app.monitoring.port.MetricsRollupRepository
import com.miyou.app.monitoring.repository.SpringDataMetricsRollupRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Repository
class MongoMetricsRollupRepository(
    private val repository: SpringDataMetricsRollupRepository,
) : MetricsRollupRepository {
    override fun save(rollup: MetricsRollup): Mono<MetricsRollup> =
        Mono
            .just(rollup)
            .map(MetricsRollupDocument::fromDomain)
            .flatMap(repository::save)
            .map(MetricsRollupDocument::toDomain)

    override fun findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
        granularity: MetricsGranularity,
        startTime: Instant,
        endTime: Instant,
    ): Flux<MetricsRollup> =
        repository
            .findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
                granularity.name,
                startTime,
                endTime,
            ).map(MetricsRollupDocument::toDomain)
}
