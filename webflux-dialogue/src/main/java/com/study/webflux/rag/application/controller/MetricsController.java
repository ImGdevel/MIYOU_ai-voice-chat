package com.study.webflux.rag.application.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.study.webflux.rag.domain.model.metrics.PerformanceMetrics;
import com.study.webflux.rag.domain.model.metrics.UsageAnalytics;
import com.study.webflux.rag.domain.port.in.MetricsQueryUseCase;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

	private final MetricsQueryUseCase metricsQueryUseCase;

	@GetMapping("/performance")
	public Flux<PerformanceMetrics> getPerformanceMetrics(
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

		if (startTime == null) {
			startTime = Instant.now().minus(24, ChronoUnit.HOURS);
		}
		if (endTime == null) {
			endTime = Instant.now();
		}

		return metricsQueryUseCase.getPerformanceMetricsByTimeRange(startTime, endTime);
	}

	@GetMapping("/performance/slow")
	public Flux<PerformanceMetrics> getSlowPipelines(
		@RequestParam(defaultValue = "1000") long thresholdMillis,
		@RequestParam(defaultValue = "20") int limit) {

		return metricsQueryUseCase.getSlowPipelines(thresholdMillis, limit);
	}

	@GetMapping("/performance/recent")
	public Flux<PerformanceMetrics> getRecentPerformanceMetrics(
		@RequestParam(defaultValue = "20") int limit) {

		return metricsQueryUseCase.getRecentPerformanceMetrics(limit);
	}

	@GetMapping("/usage")
	public Flux<UsageAnalytics> getUsageAnalytics(
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

		if (startTime == null) {
			startTime = Instant.now().minus(24, ChronoUnit.HOURS);
		}
		if (endTime == null) {
			endTime = Instant.now();
		}

		return metricsQueryUseCase.getUsageAnalyticsByTimeRange(startTime, endTime);
	}

	@GetMapping("/usage/high-tokens")
	public Flux<UsageAnalytics> getHighTokenUsage(
		@RequestParam(defaultValue = "1000") int tokenThreshold,
		@RequestParam(defaultValue = "20") int limit) {

		return metricsQueryUseCase.getHighTokenUsage(tokenThreshold, limit);
	}

	@GetMapping("/usage/recent")
	public Flux<UsageAnalytics> getRecentUsageAnalytics(
		@RequestParam(defaultValue = "20") int limit) {

		return metricsQueryUseCase.getRecentUsageAnalytics(limit);
	}

	@GetMapping("/usage/summary")
	public Mono<UsageSummary> getUsageSummary(
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

		if (startTime == null) {
			startTime = Instant.now().minus(24, ChronoUnit.HOURS);
		}
		if (endTime == null) {
			endTime = Instant.now();
		}

		Instant finalStartTime = startTime;
		Instant finalEndTime = endTime;

		return Mono.zip(
			metricsQueryUseCase.getTotalRequestCount(finalStartTime, finalEndTime),
			metricsQueryUseCase.getTotalTokenUsage(finalStartTime, finalEndTime))
			.map(tuple -> new UsageSummary(
				finalStartTime,
				finalEndTime,
				tuple.getT1(),
				tuple.getT2()));
	}

	public record UsageSummary(
		Instant startTime,
		Instant endTime,
		long totalRequests,
		long totalTokens) {
	}
}
