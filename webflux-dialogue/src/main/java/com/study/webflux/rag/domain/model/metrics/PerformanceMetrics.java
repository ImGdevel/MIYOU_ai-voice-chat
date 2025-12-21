package com.study.webflux.rag.domain.model.metrics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PerformanceMetrics(
	String pipelineId,
	String status,
	Instant startedAt,
	Instant finishedAt,
	long totalDurationMillis,
	Long firstResponseLatencyMillis,
	Long lastResponseLatencyMillis,
	List<StagePerformance> stages,
	Map<String, Object> systemAttributes
) {
	public record StagePerformance(
		String stageName,
		String status,
		Instant startedAt,
		Instant finishedAt,
		long durationMillis,
		Map<String, Object> attributes) {
	}

	public static PerformanceMetrics fromPipelineSummary(
		String pipelineId,
		String status,
		Instant startedAt,
		Instant finishedAt,
		long durationMillis,
		Long firstResponseLatencyMillis,
		Long lastResponseLatencyMillis,
		List<StagePerformance> stages,
		Map<String, Object> systemAttributes) {
		return new PerformanceMetrics(
			pipelineId,
			status,
			startedAt,
			finishedAt,
			durationMillis,
			firstResponseLatencyMillis,
			lastResponseLatencyMillis,
			stages,
			systemAttributes);
	}
}
