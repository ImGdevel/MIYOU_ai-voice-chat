package com.study.webflux.rag.infrastructure.adapter.persistence.mongodb;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.infrastructure.adapter.persistence.mongodb.entity.UsageAnalyticsEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SpringDataUsageAnalyticsRepository
	extends
		ReactiveMongoRepository<UsageAnalyticsEntity, String> {

	Flux<UsageAnalyticsEntity> findByTimestampBetweenOrderByTimestampDesc(Instant startTime,
		Instant endTime);

	@Query("{ 'llmUsage.model': ?0 }")
	Flux<UsageAnalyticsEntity> findByModel(String model, Pageable pageable);

	@Query("{ 'llmUsage.tokenCount': { $gte: ?0 } }")
	Flux<UsageAnalyticsEntity> findHighTokenUsage(int tokenThreshold, Pageable pageable);

	Flux<UsageAnalyticsEntity> findAllByOrderByTimestampDesc(Pageable pageable);

	Mono<Long> countByTimestampBetween(Instant startTime, Instant endTime);

	@Aggregation(pipeline = {
		"{ $match: { timestamp: { $gte: ?0, $lte: ?1 } } }",
		"{ $group: { _id: null, total: { $sum: '$llmUsage.tokenCount' } } }"
	})
	Mono<Long> sumTokensByTimeRange(Instant startTime, Instant endTime);

	@Aggregation(pipeline = {
		"{ $group: { _id: null, total: { $sum: '$llmUsage.tokenCount' } } }"
	})
	Mono<Long> sumAllTokens();

	@Aggregation(pipeline = {
		"{ $group: { _id: null, avg: { $avg: '$responseMetrics.totalDurationMillis' } } }"
	})
	Mono<Double> averageAllResponseTime();
}
