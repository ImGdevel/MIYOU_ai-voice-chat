package com.study.webflux.rag.application.monitoring.monitor;

public interface PipelineMetricsReporter {
	void report(DialoguePipelineTracker.PipelineSummary summary);
}
