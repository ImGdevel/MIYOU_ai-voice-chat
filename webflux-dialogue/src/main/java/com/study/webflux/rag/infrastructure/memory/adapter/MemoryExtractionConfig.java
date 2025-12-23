package com.study.webflux.rag.infrastructure.memory.adapter;

public record MemoryExtractionConfig(
	String model,
	int conversationThreshold,
	float importanceBoost,
	float importanceThreshold
) {
}
