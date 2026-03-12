package com.study.webflux.rag.domain.monitoring.port;

import com.study.webflux.rag.domain.monitoring.model.PipelineSummary;

public interface PipelineMetricsReporter {
	void report(PipelineSummary summary);
}
