package com.study.webflux.rag.domain.monitoring.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PipelineSummary(
	String pipelineId,
	PipelineStatus status,
	Instant startedAt,
	Instant finishedAt,
	Map<String, Object> attributes,
	List<StageSnapshot> stages,
	List<String> llmOutputs,
	Long firstResponseLatencyMillis,
	Long lastResponseLatencyMillis
) {

	public long durationMillis() {
		if (startedAt == null || finishedAt == null) {
			return -1L;
		}
		return Duration.between(startedAt, finishedAt).toMillis();
	}
}
