package com.miyou.app.domain.monitoring.port

import com.miyou.app.domain.monitoring.model.UsageAnalytics
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface UsageAnalyticsRepository {
    fun save(analytics: UsageAnalytics): Mono<UsageAnalytics>

    fun findById(pipelineId: String): Mono<UsageAnalytics>

    fun findByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Flux<UsageAnalytics>

    fun findByModel(
        model: String,
        limit: Int,
    ): Flux<UsageAnalytics>

    fun findHighTokenUsage(
        tokenThreshold: Int,
        limit: Int,
    ): Flux<UsageAnalytics>

    fun findRecent(limit: Int): Flux<UsageAnalytics>

    fun countByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Mono<Long>

    fun sumTokensByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Mono<Long>

    fun count(): Mono<Long>

    fun sumTokens(): Mono<Long>

    fun averageResponseTime(): Mono<Double>
}
