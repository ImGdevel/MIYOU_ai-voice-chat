package com.study.webflux.rag.domain.model.metrics;

public record PipelineDetail(
	String pipelineId,
	PerformanceMetrics performance,
	UsageAnalytics usage
) {
}
