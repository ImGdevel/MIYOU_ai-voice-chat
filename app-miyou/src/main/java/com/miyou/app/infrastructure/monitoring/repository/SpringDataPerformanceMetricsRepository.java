package com.miyou.app.infrastructure.monitoring.repository;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.miyou.app.infrastructure.monitoring.document.PerformanceMetricsDocument;
import reactor.core.publisher.Flux;

public interface SpringDataPerformanceMetricsRepository
	extends
		ReactiveMongoRepository<PerformanceMetricsDocument, String> {

	Flux<PerformanceMetricsDocument> findByStartedAtBetweenOrderByStartedAtDesc(Instant startTime,
		Instant endTime);

	Flux<PerformanceMetricsDocument> findByStatusOrderByStartedAtDesc(String status,
		Pageable pageable);

	Flux<PerformanceMetricsDocument> findByTotalDurationMillisGreaterThanEqual(long thresholdMillis,
		Pageable pageable);

	Flux<PerformanceMetricsDocument> findAllByOrderByStartedAtDesc(Pageable pageable);
}
