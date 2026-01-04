package com.study.webflux.rag.infrastructure.monitoring.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.domain.monitoring.entity.StagePerformanceRollupEntity;
import reactor.core.publisher.Flux;

public interface SpringDataStagePerformanceRollupRepository
	extends
		ReactiveMongoRepository<StagePerformanceRollupEntity, String> {

	Flux<StagePerformanceRollupEntity> findByGranularityAndBucketStartBetween(
		String granularity,
		Instant startTime,
		Instant endTime);
}
