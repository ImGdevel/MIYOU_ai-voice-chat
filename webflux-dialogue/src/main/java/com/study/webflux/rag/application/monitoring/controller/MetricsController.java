package com.study.webflux.rag.application.monitoring.controller;

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

import com.study.webflux.rag.domain.cost.service.CostCalculationService;
import com.study.webflux.rag.domain.monitoring.model.MetricsGranularity;
import com.study.webflux.rag.domain.monitoring.model.MetricsRollup;
import com.study.webflux.rag.domain.monitoring.model.PerformanceMetrics;
import com.study.webflux.rag.domain.monitoring.model.PipelineDetail;
import com.study.webflux.rag.domain.monitoring.model.StagePerformanceSummary;
import com.study.webflux.rag.domain.monitoring.model.UsageAnalytics;
import com.study.webflux.rag.domain.monitoring.port.MetricsQueryUseCase;
import jakarta.validation.constraints.Min;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 파이프라인 성능과 사용량 지표 조회용 REST 컨트롤러입니다. */
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
@Validated
public class MetricsController {

	private final MetricsQueryUseCase metricsQueryUseCase;

	/** 지정된 시간 범위의 성능 지표를 조회합니다. */
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

	/** 최신 성능 지표를 지정한 개수만큼 반환합니다. */
	@GetMapping("/performance/recent")
	public Flux<PerformanceMetrics> getRecentPerformanceMetrics(
		@RequestParam(defaultValue = "20") @Min(1) int limit) {

		return metricsQueryUseCase.getRecentPerformanceMetrics(limit);
	}

	/** 지정된 시간 범위의 사용량 분석을 반환합니다. */
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

	/** 최신 사용량 분석을 지정한 개수만큼 반환합니다. */
	@GetMapping("/usage/recent")
	public Flux<UsageAnalytics> getRecentUsageAnalytics(
		@RequestParam(defaultValue = "20") @Min(1) int limit) {

		return metricsQueryUseCase.getRecentUsageAnalytics(limit);
	}

	/** 지정된 시간 범위의 통합 사용량 요약을 조회합니다. */
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

	/** 전체 기간에 대한 통합 사용량 요약을 제공합니다. */
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

	/** 사용량 분석에서 누적 크레딧을 계산합니다. */
	private Mono<Long> calculateTotalCredits() {
		return metricsQueryUseCase.getRecentUsageAnalytics(MAX_CREDIT_SAMPLE)
			.map(analytics -> CostCalculationService.calculateCost(analytics))
			.map(cost -> cost.totalCredits())
			.reduce(0L, Long::sum);
	}

	/** 특정 파이프라인 실행의 상세 지표를 조회합니다. */
	@GetMapping("/pipeline/{pipelineId}")
	public Mono<PipelineDetailResponse> getPipelineDetail(@PathVariable String pipelineId) {
		return metricsQueryUseCase.getPipelineDetail(pipelineId)
			.map(PipelineDetailResponse::fromDomain)
			.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
				"Pipeline not found: " + pipelineId)));
	}

	/** 지정한 집계 수준의 롤업 지표를 반환합니다. */
	@GetMapping("/rollups")
	public Flux<MetricsRollup> getMetricsRollups(
		@RequestParam(defaultValue = "MINUTE") MetricsGranularity granularity,
		@RequestParam(defaultValue = "60") @Min(1) int limit) {
		return metricsQueryUseCase.getMetricsRollups(granularity, limit);
	}

	/** 단계별 성능 요약을 요청한 집계 수준으로 제공합니다. */
	@GetMapping("/stages/summary")
	public Flux<StagePerformanceSummary> getStagePerformanceSummary(
		@RequestParam(defaultValue = "MINUTE") MetricsGranularity granularity,
		@RequestParam(defaultValue = "60") @Min(1) int limit) {
		return metricsQueryUseCase.getStagePerformanceSummary(granularity, limit);
	}

	/** 시간 범위 사용량 요약 응답 레코드입니다. */
	public record UsageSummary(
		Instant startTime,
		Instant endTime,
		long totalRequests,
		long totalTokens) {
	}

	/** 전체 기간 사용량 요약 응답 레코드입니다. */
	public record TotalUsageSummary(
		long totalRequests,
		long totalTokens,
		double avgResponseTimeMillis,
		long totalCredits) {
	}

	/** 파이프라인 상세 지표 응답 레코드입니다. */
	public record PipelineDetailResponse(
		String pipelineId,
		PerformanceMetrics performance,
		UsageAnalytics usage) {
		/** 도메인 PipelineDetail을 API 응답으로 변환합니다. */
		public static PipelineDetailResponse fromDomain(PipelineDetail detail) {
			return new PipelineDetailResponse(
				detail.pipelineId(),
				detail.performance(),
				detail.usage());
		}
	}
}
