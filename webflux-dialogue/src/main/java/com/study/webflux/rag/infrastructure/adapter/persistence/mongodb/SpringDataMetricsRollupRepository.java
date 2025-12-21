package com.study.webflux.rag.infrastructure.adapter.persistence.mongodb;

import java.time.Instant;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.infrastructure.adapter.persistence.mongodb.entity.MetricsRollupEntity;
import reactor.core.publisher.Flux;

public interface SpringDataMetricsRollupRepository
	extends
		ReactiveMongoRepository<MetricsRollupEntity, String> {

	Flux<MetricsRollupEntity> findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
		String granularity,
		Instant startTime,
		Instant endTime);
}
