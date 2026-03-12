package com.study.webflux.rag.infrastructure.monitoring.adapter;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import com.study.webflux.rag.domain.monitoring.model.MetricsGranularity;
import com.study.webflux.rag.domain.monitoring.model.MetricsRollup;
import com.study.webflux.rag.domain.monitoring.port.MetricsRollupRepository;
import com.study.webflux.rag.infrastructure.monitoring.document.MetricsRollupDocument;
import com.study.webflux.rag.infrastructure.monitoring.repository.SpringDataMetricsRollupRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MongoMetricsRollupRepository implements MetricsRollupRepository {

	private final SpringDataMetricsRollupRepository repository;

	@Override
	public Mono<MetricsRollup> save(MetricsRollup rollup) {
		return Mono.just(rollup)
			.map(MetricsRollupDocument::fromDomain)
			.flatMap(repository::save)
			.map(MetricsRollupDocument::toDomain);
	}

	@Override
	public Flux<MetricsRollup> findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
		MetricsGranularity granularity,
		Instant startTime,
		Instant endTime) {
		return repository.findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
			granularity.name(),
			startTime,
			endTime)
			.map(MetricsRollupDocument::toDomain);
	}
}
