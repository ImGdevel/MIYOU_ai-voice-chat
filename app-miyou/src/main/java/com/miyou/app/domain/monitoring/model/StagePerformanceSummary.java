package com.miyou.app.domain.monitoring.model;

public record StagePerformanceSummary(
	String stageName,
	double avgDurationMillis
) {
}
