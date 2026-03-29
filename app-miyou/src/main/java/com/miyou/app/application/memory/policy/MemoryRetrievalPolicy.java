package com.miyou.app.application.memory.policy;

public record MemoryRetrievalPolicy(
	float importanceBoost,
	float importanceThreshold
) {
}
