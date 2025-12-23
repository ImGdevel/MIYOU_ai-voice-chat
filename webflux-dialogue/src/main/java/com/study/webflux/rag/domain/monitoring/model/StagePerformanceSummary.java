package com.study.webflux.rag.domain.monitoring.model;

public record StagePerformanceSummary(
	String stageName,
	double avgDurationMillis
) {
}
