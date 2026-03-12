package com.study.webflux.rag.infrastructure.monitoring.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.infrastructure.monitoring.document.MetricsRollupDocument;
import reactor.core.publisher.Flux;

public interface SpringDataMetricsRollupRepository
	extends
		ReactiveMongoRepository<MetricsRollupDocument, String> {

	Flux<MetricsRollupDocument> findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
		String granularity,
		Instant startTime,
		Instant endTime);
}
