package com.study.webflux.rag.infrastructure.adapter.persistence.mongodb;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import com.study.webflux.rag.domain.model.metrics.MetricsGranularity;
import com.study.webflux.rag.domain.model.metrics.MetricsRollup;
import com.study.webflux.rag.domain.port.out.MetricsRollupRepository;
import com.study.webflux.rag.infrastructure.adapter.persistence.mongodb.entity.MetricsRollupEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MongoMetricsRollupRepository implements MetricsRollupRepository {

	private final SpringDataMetricsRollupRepository repository;

	@Override
	public Mono<MetricsRollup> save(MetricsRollup rollup) {
		return Mono.just(rollup)
			.map(MetricsRollupEntity::fromDomain)
			.flatMap(repository::save)
			.map(MetricsRollupEntity::toDomain);
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
			.map(MetricsRollupEntity::toDomain);
	}
}
