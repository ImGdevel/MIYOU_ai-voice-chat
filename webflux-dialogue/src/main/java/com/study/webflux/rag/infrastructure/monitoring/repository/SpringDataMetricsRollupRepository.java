package com.study.webflux.rag.infrastructure.monitoring.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.domain.monitoring.entity.MetricsRollupEntity;
import reactor.core.publisher.Flux;

public interface SpringDataMetricsRollupRepository
	extends
		ReactiveMongoRepository<MetricsRollupEntity, String> {

	Flux<MetricsRollupEntity> findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
		String granularity,
		Instant startTime,
		Instant endTime);
}
