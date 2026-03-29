package com.miyou.app.domain.monitoring.port;

import com.miyou.app.domain.monitoring.model.PipelineSummary;

public interface PipelineMetricsReporter {
	void report(PipelineSummary summary);
}
