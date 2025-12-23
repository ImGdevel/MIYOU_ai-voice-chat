package com.study.webflux.rag.application.monitoring.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.monitoring.model.MetricsGranularity;
import com.study.webflux.rag.domain.monitoring.model.MetricsRollup;
import com.study.webflux.rag.domain.monitoring.model.PerformanceMetrics;
import com.study.webflux.rag.domain.monitoring.model.PipelineDetail;
import com.study.webflux.rag.domain.monitoring.model.StagePerformanceRollup;
import com.study.webflux.rag.domain.monitoring.model.StagePerformanceSummary;
import com.study.webflux.rag.domain.monitoring.model.UsageAnalytics;
import com.study.webflux.rag.domain.monitoring.port.MetricsQueryUseCase;
import com.study.webflux.rag.domain.monitoring.port.MetricsRollupRepository;
import com.study.webflux.rag.domain.monitoring.port.PerformanceMetricsRepository;
import com.study.webflux.rag.domain.monitoring.port.StagePerformanceRollupRepository;
import com.study.webflux.rag.domain.monitoring.port.UsageAnalyticsRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MetricsQueryService implements MetricsQueryUseCase {

	private final PerformanceMetricsRepository performanceMetricsRepository;
	private final UsageAnalyticsRepository usageAnalyticsRepository;
	private final MetricsRollupRepository metricsRollupRepository;
	private final StagePerformanceRollupRepository stagePerformanceRollupRepository;

	@Override
	public Flux<PerformanceMetrics> getPerformanceMetricsByTimeRange(Instant startTime,
		Instant endTime) {
		return performanceMetricsRepository.findByTimeRange(startTime, endTime);
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

	@Override
	public Mono<Long> getTotalRequestCount() {
		return usageAnalyticsRepository.count();
	}

	@Override
	public Mono<Long> getTotalTokenUsage() {
		return usageAnalyticsRepository.sumTokens();
	}

	@Override
	public Mono<Double> getAverageResponseTime() {
		return usageAnalyticsRepository.averageResponseTime();
	}

	@Override
	public Mono<PipelineDetail> getPipelineDetail(String pipelineId) {
		Mono<java.util.Optional<PerformanceMetrics>> performanceMono = performanceMetricsRepository
			.findById(pipelineId)
			.map(java.util.Optional::of)
			.defaultIfEmpty(java.util.Optional.empty());
		Mono<java.util.Optional<UsageAnalytics>> usageMono = usageAnalyticsRepository
			.findById(pipelineId)
			.map(java.util.Optional::of)
			.defaultIfEmpty(java.util.Optional.empty());

		return Mono.zip(performanceMono, usageMono)
			.filter(tuple -> tuple.getT1().isPresent() || tuple.getT2().isPresent())
			.map(tuple -> new PipelineDetail(
				pipelineId,
				tuple.getT1().orElse(null),
				tuple.getT2().orElse(null)));
	}

	@Override
	public Flux<MetricsRollup> getMetricsRollups(MetricsGranularity granularity, int limit) {
		Instant endTime = Instant.now();
		Instant startTime = endTime.minus(windowSize(granularity, limit));
		if (granularity == MetricsGranularity.MINUTE) {
			return metricsRollupRepository
				.findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
					granularity,
					startTime,
					endTime);
		}

		return metricsRollupRepository.findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
			MetricsGranularity.MINUTE,
			startTime,
			endTime)
			.groupBy(rollup -> bucketStart(rollup.bucketStart(), granularity))
			.flatMap(group -> group.reduce(new RollupAggregate(group.key(), granularity),
				RollupAggregate::add))
			.map(RollupAggregate::toRollup)
			.sort((a, b) -> a.bucketStart().compareTo(b.bucketStart()));
	}

	@Override
	public Flux<StagePerformanceSummary> getStagePerformanceSummary(
		MetricsGranularity granularity,
		int limit) {
		Instant endTime = Instant.now();
		Instant startTime = endTime.minus(windowSize(granularity, limit));

		return stagePerformanceRollupRepository.findByGranularityAndBucketStartBetween(
			MetricsGranularity.MINUTE,
			startTime,
			endTime)
			.groupBy(StagePerformanceRollup::stageName)
			.flatMap(group -> group.reduce(new StageAggregate(group.key()),
				StageAggregate::add))
			.map(StageAggregate::toSummary);
	}

	private Duration windowSize(MetricsGranularity granularity, int limit) {
		return switch (granularity) {
			case DAY -> java.time.Duration.ofDays(limit);
			case HOUR -> java.time.Duration.ofHours(limit);
			case MINUTE -> java.time.Duration.ofMinutes(limit);
		};
	}

	private Instant bucketStart(Instant instant, MetricsGranularity granularity) {
		return switch (granularity) {
			case DAY -> instant.truncatedTo(ChronoUnit.DAYS);
			case HOUR -> instant.truncatedTo(ChronoUnit.HOURS);
			case MINUTE -> instant.truncatedTo(ChronoUnit.MINUTES);
		};
	}

	private static final class RollupAggregate {
		private final Instant bucketStart;
		private final MetricsGranularity granularity;
		private long requestCount;
		private long totalTokens;
		private long totalDurationMillis;

		private RollupAggregate(Instant bucketStart, MetricsGranularity granularity) {
			this.bucketStart = bucketStart;
			this.granularity = granularity;
		}

		private RollupAggregate add(MetricsRollup rollup) {
			requestCount += rollup.requestCount();
			totalTokens += rollup.totalTokens();
			totalDurationMillis += rollup.totalDurationMillis();
			return this;
		}

		private MetricsRollup toRollup() {
			double avg = requestCount == 0 ? 0 : (double) totalDurationMillis / requestCount;
			return new MetricsRollup(
				bucketStart,
				granularity,
				requestCount,
				totalTokens,
				totalDurationMillis,
				avg);
		}
	}

	private static final class StageAggregate {
		private final String stageName;
		private long count;
		private long totalDurationMillis;

		private StageAggregate(String stageName) {
			this.stageName = stageName;
		}

		private StageAggregate add(StagePerformanceRollup rollup) {
			count += rollup.count();
			totalDurationMillis += rollup.totalDurationMillis();
			return this;
		}

		private StagePerformanceSummary toSummary() {
			double avg = count == 0 ? 0 : (double) totalDurationMillis / count;
			return new StagePerformanceSummary(stageName, avg);
		}
	}
}
