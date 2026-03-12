package com.study.webflux.rag.domain.monitoring.model;

import java.time.Instant;
import java.util.Map;

public record StageSnapshot(
	DialoguePipelineStage stage,
	StageStatus status,
	Instant startedAt,
	Instant finishedAt,
	long durationMillis,
	Map<String, Object> attributes
) {
	public StageSnapshot {
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
	}
}
