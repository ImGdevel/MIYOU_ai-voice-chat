package com.miyou.app.domain.monitoring.model;

public record PipelineDetail(
	String pipelineId,
	com.miyou.app.domain.monitoring.model.PerformanceMetrics performance,
	com.miyou.app.domain.monitoring.model.UsageAnalytics usage
) {
}
