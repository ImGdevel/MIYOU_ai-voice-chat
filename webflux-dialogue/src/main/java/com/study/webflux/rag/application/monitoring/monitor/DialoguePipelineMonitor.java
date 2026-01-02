package com.study.webflux.rag.application.monitoring.monitor;

import java.time.Clock;

import org.springframework.stereotype.Component;

@Component
public class DialoguePipelineMonitor {

	private final PipelineMetricsReporter reporter;
	private final Clock clock;

	public DialoguePipelineMonitor(PipelineMetricsReporter reporter, Clock clock) {
		this.reporter = reporter;
		this.clock = clock;
	}

	public DialoguePipelineTracker create(String inputText) {
		return new DialoguePipelineTracker(inputText, reporter, clock);
	}
}
