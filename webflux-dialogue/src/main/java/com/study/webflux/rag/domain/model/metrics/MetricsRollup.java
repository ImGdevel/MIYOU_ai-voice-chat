package com.study.webflux.rag.domain.model.metrics;

import java.time.Instant;

public record MetricsRollup(
	Instant bucketStart,
	MetricsGranularity granularity,
	long requestCount,
	long totalTokens,
	long totalDurationMillis,
	double avgResponseMillis
) {
}
