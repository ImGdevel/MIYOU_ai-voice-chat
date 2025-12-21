package com.study.webflux.rag.domain.model.metrics;

import java.time.Instant;

public record StagePerformanceRollup(
	Instant bucketStart,
	MetricsGranularity granularity,
	String stageName,
	long count,
	long totalDurationMillis,
	double avgDurationMillis
) {
}
