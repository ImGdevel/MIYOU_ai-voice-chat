package com.miyou.app.infrastructure.monitoring.repository

import com.miyou.app.infrastructure.monitoring.document.UsageAnalyticsDocument
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface SpringDataUsageAnalyticsRepository : ReactiveMongoRepository<UsageAnalyticsDocument, String> {
    fun findByTimestampBetweenOrderByTimestampDesc(
        startTime: Instant,
        endTime: Instant,
    ): Flux<UsageAnalyticsDocument>

    @Query("{ 'llmUsage.model': ?0 }")
    fun findByModel(
        model: String,
        pageable: Pageable,
    ): Flux<UsageAnalyticsDocument>

    @Query("{ 'llmUsage.totalTokens': { \$gte: ?0 } }")
    fun findHighTokenUsage(
        tokenThreshold: Int,
        pageable: Pageable,
    ): Flux<UsageAnalyticsDocument>

    fun findAllByOrderByTimestampDesc(pageable: Pageable): Flux<UsageAnalyticsDocument>

    fun countByTimestampBetween(
        startTime: Instant,
        endTime: Instant,
    ): Mono<Long>

    @Aggregation(
        pipeline = [
            "{ \$match: { timestamp: { \$gte: ?0, \$lte: ?1 } } }",
            "{ \$group: { _id: null, total: { \$sum: '\$llmUsage.totalTokens' } } }",
        ],
    )
    fun sumTokensByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Mono<Long>

    @Aggregation(
        pipeline = [
            "{ \$group: { _id: null, total: { \$sum: '\$llmUsage.totalTokens' } } }",
        ],
    )
    fun sumAllTokens(): Mono<Long>

    @Aggregation(
        pipeline = [
            "{ \$group: { _id: null, avg: { \$avg: '\$responseMetrics.totalDurationMillis' } } }",
        ],
    )
    fun averageAllResponseTime(): Mono<Double>
}
