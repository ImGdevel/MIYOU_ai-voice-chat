package com.study.webflux.rag.infrastructure.adapter.persistence.mongodb;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.study.webflux.rag.domain.model.metrics.PerformanceMetrics;
import com.study.webflux.rag.domain.port.out.PerformanceMetricsRepository;
import com.study.webflux.rag.infrastructure.adapter.persistence.mongodb.entity.PerformanceMetricsEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MongoPerformanceMetricsRepository implements PerformanceMetricsRepository {

	private final SpringDataPerformanceMetricsRepository repository;

	@Override
	public Mono<PerformanceMetrics> save(PerformanceMetrics metrics) {
		return Mono.just(metrics)
			.map(PerformanceMetricsEntity::fromDomain)
			.flatMap(repository::save)
			.map(PerformanceMetricsEntity::toDomain);
	}

	@Override
	public Mono<PerformanceMetrics> findById(String pipelineId) {
		return repository.findById(pipelineId)
			.map(PerformanceMetricsEntity::toDomain);
	}

	@Override
	public Flux<PerformanceMetrics> findByTimeRange(Instant startTime, Instant endTime) {
		return repository.findByStartedAtBetweenOrderByStartedAtDesc(startTime, endTime)
			.map(PerformanceMetricsEntity::toDomain);
	}

	@Override
	public Flux<PerformanceMetrics> findByStatus(String status, int limit) {
		return repository.findByStatusOrderByStartedAtDesc(status, PageRequest.of(0, limit))
			.map(PerformanceMetricsEntity::toDomain);
	}

	@Override
	public Flux<PerformanceMetrics> findSlowPipelines(long thresholdMillis, int limit) {
		return repository.findSlowPipelines(thresholdMillis, PageRequest.of(0, limit))
			.map(PerformanceMetricsEntity::toDomain);
	}

	@Override
	public Flux<PerformanceMetrics> findRecent(int limit) {
		return repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
			.map(PerformanceMetricsEntity::toDomain);
	}
}
