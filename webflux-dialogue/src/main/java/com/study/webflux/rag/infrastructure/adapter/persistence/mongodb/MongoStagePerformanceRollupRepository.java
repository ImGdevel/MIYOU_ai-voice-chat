package com.study.webflux.rag.infrastructure.adapter.persistence.mongodb;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import com.study.webflux.rag.domain.model.metrics.MetricsGranularity;
import com.study.webflux.rag.domain.model.metrics.StagePerformanceRollup;
import com.study.webflux.rag.domain.port.out.StagePerformanceRollupRepository;
import com.study.webflux.rag.infrastructure.adapter.persistence.mongodb.entity.StagePerformanceRollupEntity;
import reactor.core.publisher.Flux;

@Repository
@RequiredArgsConstructor
public class MongoStagePerformanceRollupRepository implements StagePerformanceRollupRepository {

	private final SpringDataStagePerformanceRollupRepository repository;

	@Override
	public Flux<StagePerformanceRollup> saveAll(Flux<StagePerformanceRollup> rollups) {
		return repository.saveAll(rollups.map(StagePerformanceRollupEntity::fromDomain))
			.map(StagePerformanceRollupEntity::toDomain);
	}

	@Override
	public Flux<StagePerformanceRollup> findByGranularityAndBucketStartBetween(
		MetricsGranularity granularity,
		Instant startTime,
		Instant endTime) {
		return repository.findByGranularityAndBucketStartBetween(
			granularity.name(),
			startTime,
			endTime)
			.map(StagePerformanceRollupEntity::toDomain);
	}
}
