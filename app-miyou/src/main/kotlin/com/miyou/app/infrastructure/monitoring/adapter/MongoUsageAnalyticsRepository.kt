package com.miyou.app.infrastructure.monitoring.adapter

import com.miyou.app.domain.monitoring.model.UsageAnalytics
import com.miyou.app.domain.monitoring.port.UsageAnalyticsRepository
import com.miyou.app.infrastructure.monitoring.document.UsageAnalyticsDocument
import com.miyou.app.infrastructure.monitoring.repository.SpringDataUsageAnalyticsRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Repository
class MongoUsageAnalyticsRepository(
    private val repository: SpringDataUsageAnalyticsRepository,
) : UsageAnalyticsRepository {
    override fun save(analytics: UsageAnalytics): Mono<UsageAnalytics> =
        Mono
            .just(analytics)
            .map(UsageAnalyticsDocument::fromDomain)
            .flatMap(repository::save)
            .map(UsageAnalyticsDocument::toDomain)

    override fun findById(pipelineId: String): Mono<UsageAnalytics> =
        repository
            .findById(pipelineId)
            .map(UsageAnalyticsDocument::toDomain)

    override fun findByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Flux<UsageAnalytics> =
        repository
            .findByTimestampBetweenOrderByTimestampDesc(startTime, endTime)
            .map(UsageAnalyticsDocument::toDomain)

    override fun findByModel(
        model: String,
        limit: Int,
    ): Flux<UsageAnalytics> =
        repository
            .findByModel(model, PageRequest.of(0, limit))
            .map(UsageAnalyticsDocument::toDomain)

    override fun findHighTokenUsage(
        tokenThreshold: Int,
        limit: Int,
    ): Flux<UsageAnalytics> =
        repository
            .findHighTokenUsage(tokenThreshold, PageRequest.of(0, limit))
            .map(UsageAnalyticsDocument::toDomain)

    override fun findRecent(limit: Int): Flux<UsageAnalytics> =
        repository
            .findAllByOrderByTimestampDesc(PageRequest.of(0, limit))
            .map(UsageAnalyticsDocument::toDomain)

    override fun countByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Mono<Long> = repository.countByTimestampBetween(startTime, endTime)

    override fun sumTokensByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Mono<Long> =
        repository
            .sumTokensByTimeRange(startTime, endTime)
            .defaultIfEmpty(0L)

    override fun count(): Mono<Long> = repository.count()

    override fun sumTokens(): Mono<Long> =
        repository
            .sumAllTokens()
            .defaultIfEmpty(0L)

    override fun averageResponseTime(): Mono<Double> =
        repository
            .averageAllResponseTime()
            .defaultIfEmpty(0.0)
}
