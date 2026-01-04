package com.study.webflux.rag.domain.monitoring.model;

public record PipelineDetail(
	String pipelineId,
	com.study.webflux.rag.domain.monitoring.model.PerformanceMetrics performance,
	com.study.webflux.rag.domain.monitoring.model.UsageAnalytics usage
) {
}
