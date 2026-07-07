package com.miyou.app.infrastructure.monitoring.adapter

import com.miyou.app.domain.monitoring.model.PerformanceMetrics
import com.miyou.app.domain.monitoring.port.PerformanceMetricsRepository
import com.miyou.app.infrastructure.monitoring.document.PerformanceMetricsDocument
import com.miyou.app.infrastructure.monitoring.repository.SpringDataPerformanceMetricsRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Repository
class MongoPerformanceMetricsRepository(
    private val repository: SpringDataPerformanceMetricsRepository,
) : PerformanceMetricsRepository {
    override fun save(metrics: PerformanceMetrics): Mono<PerformanceMetrics> =
        Mono
            .just(metrics)
            .map(PerformanceMetricsDocument::fromDomain)
            .flatMap(repository::save)
            .map(PerformanceMetricsDocument::toDomain)

    override fun findById(pipelineId: String): Mono<PerformanceMetrics> =
        repository
            .findById(pipelineId)
            .map(PerformanceMetricsDocument::toDomain)

    override fun findByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Flux<PerformanceMetrics> =
        repository
            .findByStartedAtBetweenOrderByStartedAtDesc(startTime, endTime)
            .map(PerformanceMetricsDocument::toDomain)

    override fun findByStatus(
        status: String,
        limit: Int,
    ): Flux<PerformanceMetrics> {
        val pageSize = limit.coerceAtLeast(1)
        return repository
            .findByStatusOrderByStartedAtDesc(status, PageRequest.of(0, pageSize))
            .map(PerformanceMetricsDocument::toDomain)
    }

    override fun findSlowPipelines(
        thresholdMillis: Long,
        limit: Int,
    ): Flux<PerformanceMetrics> {
        val pageSize = limit.coerceAtLeast(1)
        return repository
            .findByTotalDurationMillisGreaterThanEqual(
                thresholdMillis,
                PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "totalDurationMillis")),
            ).map(PerformanceMetricsDocument::toDomain)
    }

    override fun findRecent(limit: Int): Flux<PerformanceMetrics> {
        val pageSize = limit.coerceAtLeast(1)
        return repository
            .findAllByOrderByStartedAtDesc(PageRequest.of(0, pageSize))
            .map(PerformanceMetricsDocument::toDomain)
    }
}
