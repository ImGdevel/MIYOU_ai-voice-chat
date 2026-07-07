package com.miyou.app.infrastructure.monitoring.repository

import com.miyou.app.infrastructure.monitoring.document.PerformanceMetricsDocument
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import java.time.Instant

interface SpringDataPerformanceMetricsRepository : ReactiveMongoRepository<PerformanceMetricsDocument, String> {
    fun findByStartedAtBetweenOrderByStartedAtDesc(
        startTime: Instant,
        endTime: Instant,
    ): Flux<PerformanceMetricsDocument>

    fun findByStatusOrderByStartedAtDesc(
        status: String,
        pageable: Pageable,
    ): Flux<PerformanceMetricsDocument>

    fun findByTotalDurationMillisGreaterThanEqual(
        thresholdMillis: Long,
        pageable: Pageable,
    ): Flux<PerformanceMetricsDocument>

    fun findAllByOrderByStartedAtDesc(pageable: Pageable): Flux<PerformanceMetricsDocument>
}
