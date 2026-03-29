package com.miyou.app.infrastructure.memory.adapter;

public record MemoryExtractionConfig(
	String model,
	int conversationThreshold,
	float importanceBoost,
	float importanceThreshold
) {
}
