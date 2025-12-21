package com.study.webflux.rag.domain.model.metrics;

public record StagePerformanceSummary(
	String stageName,
	double avgDurationMillis
) {
}
