package com.study.webflux.rag.infrastructure.monitoring.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.infrastructure.monitoring.document.StagePerformanceRollupDocument;
import reactor.core.publisher.Flux;

public interface SpringDataStagePerformanceRollupRepository
	extends
		ReactiveMongoRepository<StagePerformanceRollupDocument, String> {

	Flux<StagePerformanceRollupDocument> findByGranularityAndBucketStartBetween(
		String granularity,
		Instant startTime,
		Instant endTime);
}
