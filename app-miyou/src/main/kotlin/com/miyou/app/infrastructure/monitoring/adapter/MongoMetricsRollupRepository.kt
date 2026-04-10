package com.miyou.app.infrastructure.monitoring.adapter

import com.miyou.app.domain.monitoring.model.MetricsGranularity
import com.miyou.app.domain.monitoring.model.MetricsRollup
import com.miyou.app.domain.monitoring.port.MetricsRollupRepository
import com.miyou.app.infrastructure.monitoring.document.MetricsRollupDocument
import com.miyou.app.infrastructure.monitoring.repository.SpringDataMetricsRollupRepository
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
