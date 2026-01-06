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

	/**
	 * 입력 텍스트 기준으로 파이프라인 추적기를 생성합니다.
	 */
	public DialoguePipelineTracker create(String inputText) {
		return new DialoguePipelineTracker(inputText, reporter, clock);
	}
}
