package com.study.webflux.rag.infrastructure.monitoring.adapter;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.study.webflux.rag.domain.monitoring.model.PerformanceMetrics;
import com.study.webflux.rag.domain.monitoring.port.PerformanceMetricsRepository;
import com.study.webflux.rag.infrastructure.monitoring.document.PerformanceMetricsDocument;
import com.study.webflux.rag.infrastructure.monitoring.repository.SpringDataPerformanceMetricsRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MongoPerformanceMetricsRepository implements PerformanceMetricsRepository {

	private final SpringDataPerformanceMetricsRepository repository;

	@Override
	public Mono<PerformanceMetrics> save(PerformanceMetrics metrics) {
		return Mono.just(metrics)
			.map(PerformanceMetricsDocument::fromDomain)
			.flatMap(repository::save)
			.map(PerformanceMetricsDocument::toDomain);
	}

	@Override
	public Mono<PerformanceMetrics> findById(String pipelineId) {
		return repository.findById(pipelineId)
			.map(PerformanceMetricsDocument::toDomain);
	}

	@Override
	public Flux<PerformanceMetrics> findByTimeRange(Instant startTime, Instant endTime) {
		return repository.findByStartedAtBetweenOrderByStartedAtDesc(startTime, endTime)
			.map(PerformanceMetricsDocument::toDomain);
	}

	@Override
	public Flux<PerformanceMetrics> findByStatus(String status, int limit) {
		int pageSize = Math.max(1, limit);
		return repository.findByStatusOrderByStartedAtDesc(status, PageRequest.of(0, pageSize))
			.map(PerformanceMetricsDocument::toDomain);
	}

	@Override
	public Flux<PerformanceMetrics> findSlowPipelines(long thresholdMillis, int limit) {
		int pageSize = Math.max(1, limit);
		return repository.findSlowPipelines(thresholdMillis, PageRequest.of(0, pageSize))
			.map(PerformanceMetricsDocument::toDomain);
	}

	@Override
	public Flux<PerformanceMetrics> findRecent(int limit) {
		int pageSize = Math.max(1, limit);
		return repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, pageSize))
			.map(PerformanceMetricsDocument::toDomain);
	}
}
