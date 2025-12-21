package com.study.webflux.rag.application.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.study.webflux.rag.domain.model.metrics.MetricsGranularity;
import com.study.webflux.rag.domain.model.metrics.MetricsRollup;
import com.study.webflux.rag.domain.model.metrics.PerformanceMetrics;
import com.study.webflux.rag.domain.model.metrics.PipelineDetail;
import com.study.webflux.rag.domain.model.metrics.StagePerformanceSummary;
import com.study.webflux.rag.domain.model.metrics.UsageAnalytics;
import com.study.webflux.rag.domain.port.in.MetricsQueryUseCase;
import jakarta.validation.constraints.Min;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
@Validated
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

	@GetMapping("/performance/recent")
	public Flux<PerformanceMetrics> getRecentPerformanceMetrics(
		@RequestParam(defaultValue = "20") @Min(1) int limit) {

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

	@GetMapping("/usage/recent")
	public Flux<UsageAnalytics> getRecentUsageAnalytics(
		@RequestParam(defaultValue = "20") @Min(1) int limit) {

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

	@GetMapping("/usage/summary/total")
	public Mono<TotalUsageSummary> getTotalUsageSummary() {
		return Mono.zip(
			metricsQueryUseCase.getTotalRequestCount(),
			metricsQueryUseCase.getTotalTokenUsage(),
			metricsQueryUseCase.getAverageResponseTime(),
			calculateTotalCredits())
			.map(tuple -> new TotalUsageSummary(
				tuple.getT1(),
				tuple.getT2(),
				tuple.getT3(),
				tuple.getT4()));
	}

	private static final int MAX_CREDIT_SAMPLE = 10_000;

	private Mono<Long> calculateTotalCredits() {
		// 대량 데이터 로딩 방지: 합리적 상한선으로 계산
		return metricsQueryUseCase.getRecentUsageAnalytics(MAX_CREDIT_SAMPLE)
			.map(analytics -> com.study.webflux.rag.domain.service.CostCalculationService
				.calculateCost(analytics))
			.map(cost -> cost.totalCredits())
			.reduce(0L, Long::sum);
	}

	@GetMapping("/pipeline/{pipelineId}")
	public Mono<PipelineDetailResponse> getPipelineDetail(@PathVariable String pipelineId) {
		return metricsQueryUseCase.getPipelineDetail(pipelineId)
			.map(PipelineDetailResponse::fromDomain)
			.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
				"Pipeline not found: " + pipelineId)));
	}

	@GetMapping("/rollups")
	public Flux<MetricsRollup> getMetricsRollups(
		@RequestParam(defaultValue = "MINUTE") MetricsGranularity granularity,
		@RequestParam(defaultValue = "60") @Min(1) int limit) {
		return metricsQueryUseCase.getMetricsRollups(granularity, limit);
	}

	@GetMapping("/stages/summary")
	public Flux<StagePerformanceSummary> getStagePerformanceSummary(
		@RequestParam(defaultValue = "MINUTE") MetricsGranularity granularity,
		@RequestParam(defaultValue = "60") @Min(1) int limit) {
		return metricsQueryUseCase.getStagePerformanceSummary(granularity, limit);
	}

	public record UsageSummary(
		Instant startTime,
		Instant endTime,
		long totalRequests,
		long totalTokens) {
	}

	public record TotalUsageSummary(
		long totalRequests,
		long totalTokens,
		double avgResponseTimeMillis,
		long totalCredits) {
	}

	public record PipelineDetailResponse(
		String pipelineId,
		PerformanceMetrics performance,
		UsageAnalytics usage) {
		public static PipelineDetailResponse fromDomain(PipelineDetail detail) {
			return new PipelineDetailResponse(
				detail.pipelineId(),
				detail.performance(),
				detail.usage());
		}
	}
}
