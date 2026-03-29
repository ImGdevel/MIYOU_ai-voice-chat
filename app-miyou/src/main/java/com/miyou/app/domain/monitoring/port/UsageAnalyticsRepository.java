package com.miyou.app.domain.monitoring.port;

import java.time.Instant;

import com.miyou.app.domain.monitoring.model.UsageAnalytics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsageAnalyticsRepository {
	Mono<UsageAnalytics> save(UsageAnalytics analytics);

	Mono<UsageAnalytics> findById(String pipelineId);

	Flux<UsageAnalytics> findByTimeRange(Instant startTime, Instant endTime);

	Flux<UsageAnalytics> findByModel(String model, int limit);

	Flux<UsageAnalytics> findHighTokenUsage(int tokenThreshold, int limit);

	Flux<UsageAnalytics> findRecent(int limit);

	Mono<Long> countByTimeRange(Instant startTime, Instant endTime);

	Mono<Long> sumTokensByTimeRange(Instant startTime, Instant endTime);

	Mono<Long> count();

	Mono<Long> sumTokens();

	Mono<Double> averageResponseTime();
}
