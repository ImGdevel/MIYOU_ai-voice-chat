package com.miyou.app.domain.monitoring.model;

import java.time.Instant;

public record StagePerformanceRollup(
	Instant bucketStart,
	com.miyou.app.domain.monitoring.model.MetricsGranularity granularity,
	String stageName,
	long count,
	long totalDurationMillis,
	double avgDurationMillis
) {
}
