package com.miyou.app.domain.monitoring.model;

import java.time.Instant;

public record MetricsRollup(
	Instant bucketStart,
	com.miyou.app.domain.monitoring.model.MetricsGranularity granularity,
	long requestCount,
	long totalTokens,
	long totalDurationMillis,
	double avgResponseMillis
) {
}
