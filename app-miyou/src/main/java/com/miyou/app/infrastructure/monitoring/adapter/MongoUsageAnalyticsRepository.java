package com.miyou.app.infrastructure.monitoring.adapter;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.miyou.app.domain.monitoring.model.UsageAnalytics;
import com.miyou.app.domain.monitoring.port.UsageAnalyticsRepository;
import com.miyou.app.infrastructure.monitoring.document.UsageAnalyticsDocument;
import com.miyou.app.infrastructure.monitoring.repository.SpringDataUsageAnalyticsRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MongoUsageAnalyticsRepository implements UsageAnalyticsRepository {

	private final SpringDataUsageAnalyticsRepository repository;

	@Override
	public Mono<UsageAnalytics> save(UsageAnalytics analytics) {
		return Mono.just(analytics)
			.map(UsageAnalyticsDocument::fromDomain)
			.flatMap(repository::save)
			.map(UsageAnalyticsDocument::toDomain);
	}

	@Override
	public Mono<UsageAnalytics> findById(String pipelineId) {
		return repository.findById(pipelineId)
			.map(UsageAnalyticsDocument::toDomain);
	}

	@Override
	public Flux<UsageAnalytics> findByTimeRange(Instant startTime, Instant endTime) {
		return repository.findByTimestampBetweenOrderByTimestampDesc(startTime, endTime)
			.map(UsageAnalyticsDocument::toDomain);
	}

	@Override
	public Flux<UsageAnalytics> findByModel(String model, int limit) {
		return repository.findByModel(model, PageRequest.of(0, limit))
			.map(UsageAnalyticsDocument::toDomain);
	}

	@Override
	public Flux<UsageAnalytics> findHighTokenUsage(int tokenThreshold, int limit) {
		return repository.findHighTokenUsage(tokenThreshold, PageRequest.of(0, limit))
			.map(UsageAnalyticsDocument::toDomain);
	}

	@Override
	public Flux<UsageAnalytics> findRecent(int limit) {
		return repository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit))
			.map(UsageAnalyticsDocument::toDomain);
	}

	@Override
	public Mono<Long> countByTimeRange(Instant startTime, Instant endTime) {
		return repository.countByTimestampBetween(startTime, endTime);
	}

	@Override
	public Mono<Long> sumTokensByTimeRange(Instant startTime, Instant endTime) {
		return repository.sumTokensByTimeRange(startTime, endTime)
			.defaultIfEmpty(0L);
	}

	@Override
	public Mono<Long> count() {
		return repository.count();
	}

	@Override
	public Mono<Long> sumTokens() {
		return repository.sumAllTokens()
			.defaultIfEmpty(0L);
	}

	@Override
	public Mono<Double> averageResponseTime() {
		return repository.averageAllResponseTime()
			.defaultIfEmpty(0.0);
	}
}
