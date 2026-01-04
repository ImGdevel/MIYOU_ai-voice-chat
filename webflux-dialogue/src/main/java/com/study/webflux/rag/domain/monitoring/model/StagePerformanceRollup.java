package com.study.webflux.rag.domain.monitoring.model;

import java.time.Instant;

public record StagePerformanceRollup(
	Instant bucketStart,
	com.study.webflux.rag.domain.monitoring.model.MetricsGranularity granularity,
	String stageName,
	long count,
	long totalDurationMillis,
	double avgDurationMillis
) {
}
