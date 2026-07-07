package com.miyou.app.domain.monitoring.port

import com.miyou.app.domain.monitoring.model.PerformanceMetrics
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface PerformanceMetricsRepository {
    fun save(metrics: PerformanceMetrics): Mono<PerformanceMetrics>

    fun findById(pipelineId: String): Mono<PerformanceMetrics>

    fun findByTimeRange(
        startTime: Instant,
        endTime: Instant,
    ): Flux<PerformanceMetrics>

    fun findByStatus(
        status: String,
        limit: Int,
    ): Flux<PerformanceMetrics>

    fun findSlowPipelines(
        thresholdMillis: Long,
        limit: Int,
    ): Flux<PerformanceMetrics>

    fun findRecent(limit: Int): Flux<PerformanceMetrics>
}
