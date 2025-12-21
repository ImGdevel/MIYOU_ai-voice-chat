package com.study.webflux.rag.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.model.metrics.MetricsGranularity;
import com.study.webflux.rag.domain.model.metrics.MetricsRollup;
import com.study.webflux.rag.domain.model.metrics.PerformanceMetrics;
import com.study.webflux.rag.domain.model.metrics.StagePerformanceRollup;
import com.study.webflux.rag.domain.model.metrics.UsageAnalytics;
import com.study.webflux.rag.domain.port.out.MetricsRollupRepository;
import com.study.webflux.rag.domain.port.out.PerformanceMetricsRepository;
import com.study.webflux.rag.domain.port.out.StagePerformanceRollupRepository;
import com.study.webflux.rag.domain.port.out.UsageAnalyticsRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsRollupScheduler {

	private final UsageAnalyticsRepository usageAnalyticsRepository;
	private final PerformanceMetricsRepository performanceMetricsRepository;
	private final MetricsRollupRepository metricsRollupRepository;
	private final StagePerformanceRollupRepository stagePerformanceRollupRepository;
	private final Clock clock = Clock.systemUTC();

	@Scheduled(cron = "0 * * * * *")
	public void rollupMinuteMetrics() {
		Instant now = clock.instant();
		Instant bucketStart = now.truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.MINUTES);
		Instant bucketEnd = bucketStart.plus(1, ChronoUnit.MINUTES);

		Mono<Void> usageRollup = usageAnalyticsRepository
			.findByTimeRange(bucketStart, bucketEnd)
			.reduce(new UsageAggregate(), UsageAggregate::add)
			.defaultIfEmpty(new UsageAggregate())
			.map(aggregate -> toMetricsRollup(bucketStart, aggregate))
			.flatMap(metricsRollupRepository::save)
			.then();

		Flux<StagePerformanceRollup> stageRollups = performanceMetricsRepository
			.findByTimeRange(bucketStart, bucketEnd)
			.flatMapIterable(
				metrics -> metrics.stages() == null ? java.util.List.of() : metrics.stages())
			.groupBy(PerformanceMetrics.StagePerformance::stageName)
			.flatMap(group -> group.reduce(new StageAggregate(group.key()), StageAggregate::add))
			.map(aggregate -> aggregate.toRollup(bucketStart));

		Mono<Void> stageRollupSave = stagePerformanceRollupRepository.saveAll(stageRollups).then();

		Mono.when(usageRollup, stageRollupSave)
			.doOnError(error -> log.warn("Failed to rollup metrics: {}", error.getMessage()))
			.subscribe();
	}

	private MetricsRollup toMetricsRollup(Instant bucketStart, UsageAggregate aggregate) {
		double avg = aggregate.requestCount == 0
			? 0
			: (double) aggregate.totalDurationMillis / aggregate.requestCount;
		return new MetricsRollup(
			bucketStart,
			MetricsGranularity.MINUTE,
			aggregate.requestCount,
			aggregate.totalTokens,
			aggregate.totalDurationMillis,
			avg);
	}

	private static final class UsageAggregate {
		private long requestCount;
		private long totalTokens;
		private long totalDurationMillis;

		private UsageAggregate add(UsageAnalytics analytics) {
			requestCount += 1;
			if (analytics.llmUsage() != null) {
				totalTokens += analytics.llmUsage().tokenCount();
			}
			if (analytics.responseMetrics() != null) {
				totalDurationMillis += analytics.responseMetrics().totalDurationMillis();
			}
			return this;
		}
	}

	private static final class StageAggregate {
		private final String stageName;
		private long count;
		private long totalDurationMillis;

		private StageAggregate(String stageName) {
			this.stageName = stageName;
		}

		private StageAggregate add(PerformanceMetrics.StagePerformance stage) {
			count += 1;
			totalDurationMillis += stage.durationMillis();
			return this;
		}

		private StagePerformanceRollup toRollup(Instant bucketStart) {
			double avg = count == 0 ? 0 : (double) totalDurationMillis / count;
			return new StagePerformanceRollup(
				bucketStart,
				MetricsGranularity.MINUTE,
				stageName,
				count,
				totalDurationMillis,
				avg);
		}
	}
}
