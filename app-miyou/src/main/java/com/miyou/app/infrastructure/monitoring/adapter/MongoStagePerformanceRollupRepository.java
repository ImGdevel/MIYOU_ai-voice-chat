package com.miyou.app.infrastructure.monitoring.adapter;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import com.miyou.app.domain.monitoring.model.MetricsGranularity;
import com.miyou.app.domain.monitoring.model.StagePerformanceRollup;
import com.miyou.app.domain.monitoring.port.StagePerformanceRollupRepository;
import com.miyou.app.infrastructure.monitoring.document.StagePerformanceRollupDocument;
import com.miyou.app.infrastructure.monitoring.repository.SpringDataStagePerformanceRollupRepository;
import reactor.core.publisher.Flux;

@Repository
@RequiredArgsConstructor
public class MongoStagePerformanceRollupRepository implements StagePerformanceRollupRepository {

	private final SpringDataStagePerformanceRollupRepository repository;

	@Override
	public Flux<StagePerformanceRollup> saveAll(Flux<StagePerformanceRollup> rollups) {
		return repository.saveAll(rollups.map(StagePerformanceRollupDocument::fromDomain))
			.map(StagePerformanceRollupDocument::toDomain);
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
			.map(StagePerformanceRollupDocument::toDomain);
	}
}
