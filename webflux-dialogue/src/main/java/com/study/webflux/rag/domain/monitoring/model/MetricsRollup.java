package com.study.webflux.rag.domain.monitoring.model;

import java.time.Instant;

public record MetricsRollup(
	Instant bucketStart,
	com.study.webflux.rag.domain.monitoring.model.MetricsGranularity granularity,
	long requestCount,
	long totalTokens,
	long totalDurationMillis,
	double avgResponseMillis
) {
}
