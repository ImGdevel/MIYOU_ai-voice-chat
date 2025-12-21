package com.study.webflux.rag.infrastructure.adapter.persistence.mongodb;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.infrastructure.adapter.persistence.mongodb.entity.PerformanceMetricsEntity;
import reactor.core.publisher.Flux;

public interface SpringDataPerformanceMetricsRepository
	extends
		ReactiveMongoRepository<PerformanceMetricsEntity, String> {

	Flux<PerformanceMetricsEntity> findByStartedAtBetweenOrderByStartedAtDesc(Instant startTime,
		Instant endTime);

	Flux<PerformanceMetricsEntity> findByStatusOrderByStartedAtDesc(String status,
		Pageable pageable);

	@Query("{ 'totalDurationMillis': { $gte: ?0 } }")
	Flux<PerformanceMetricsEntity> findSlowPipelines(long thresholdMillis, Pageable pageable);

	Flux<PerformanceMetricsEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
