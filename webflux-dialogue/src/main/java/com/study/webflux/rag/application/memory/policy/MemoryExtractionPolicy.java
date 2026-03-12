package com.study.webflux.rag.application.memory.policy;

public record MemoryExtractionPolicy(
	int conversationThreshold
) {
}
