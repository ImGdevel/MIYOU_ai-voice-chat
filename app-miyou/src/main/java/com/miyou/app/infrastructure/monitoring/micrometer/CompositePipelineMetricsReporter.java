package com.miyou.app.infrastructure.monitoring.micrometer;

import java.util.List;

import com.miyou.app.domain.monitoring.model.PipelineSummary;
import com.miyou.app.domain.monitoring.port.PipelineMetricsReporter;

/**
 * 여러 PipelineMetricsReporter를 조합하여 하나의 Reporter로 제공합니다.
 */
public class CompositePipelineMetricsReporter implements PipelineMetricsReporter {

	private final List<PipelineMetricsReporter> reporters;

	public CompositePipelineMetricsReporter(List<PipelineMetricsReporter> reporters) {
		this.reporters = List.copyOf(reporters);
	}

	@Override
	public void report(PipelineSummary summary) {
		for (PipelineMetricsReporter reporter : reporters) {
			reporter.report(summary);
		}
	}
}
