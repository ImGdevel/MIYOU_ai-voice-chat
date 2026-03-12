package com.study.webflux.rag.application.memory.policy;

public record MemoryRetrievalPolicy(
	float importanceBoost,
	float importanceThreshold
) {
}
