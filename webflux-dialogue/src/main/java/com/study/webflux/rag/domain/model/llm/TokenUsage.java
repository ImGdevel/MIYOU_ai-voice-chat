package com.study.webflux.rag.domain.model.llm;

public record TokenUsage(
	int promptTokens,
	int completionTokens,
	int totalTokens
) {
	public static TokenUsage zero() {
		return new TokenUsage(0, 0, 0);
	}

	public static TokenUsage of(int promptTokens, int completionTokens) {
		return new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens);
	}
}
