package com.study.webflux.rag.application.service;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.model.metrics.PerformanceMetrics;
import com.study.webflux.rag.domain.model.metrics.UsageAnalytics;
import com.study.webflux.rag.domain.port.in.MetricsQueryUseCase;
import com.study.webflux.rag.domain.port.out.PerformanceMetricsRepository;
import com.study.webflux.rag.domain.port.out.UsageAnalyticsRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MetricsQueryService implements MetricsQueryUseCase {

	private final PerformanceMetricsRepository performanceMetricsRepository;
	private final UsageAnalyticsRepository usageAnalyticsRepository;

	@Override
	public Flux<PerformanceMetrics> getPerformanceMetricsByTimeRange(Instant startTime,
		Instant endTime) {
		return performanceMetricsRepository.findByTimeRange(startTime, endTime);
	}

	@Override
	public Flux<PerformanceMetrics> getSlowPipelines(long thresholdMillis, int limit) {
		return performanceMetricsRepository.findSlowPipelines(thresholdMillis, limit);
	}

	@Override
	public Flux<PerformanceMetrics> getRecentPerformanceMetrics(int limit) {
		return performanceMetricsRepository.findRecent(limit);
	}

	@Override
	public Flux<UsageAnalytics> getUsageAnalyticsByTimeRange(Instant startTime, Instant endTime) {
		return usageAnalyticsRepository.findByTimeRange(startTime, endTime);
	}

	@Override
	public Flux<UsageAnalytics> getHighTokenUsage(int tokenThreshold, int limit) {
		return usageAnalyticsRepository.findHighTokenUsage(tokenThreshold, limit);
	}

	@Override
	public Flux<UsageAnalytics> getRecentUsageAnalytics(int limit) {
		return usageAnalyticsRepository.findRecent(limit);
	}

	@Override
	public Mono<Long> getTotalRequestCount(Instant startTime, Instant endTime) {
		return usageAnalyticsRepository.countByTimeRange(startTime, endTime);
	}

	@Override
	public Mono<Long> getTotalTokenUsage(Instant startTime, Instant endTime) {
		return usageAnalyticsRepository.sumTokensByTimeRange(startTime, endTime);
	}
}
