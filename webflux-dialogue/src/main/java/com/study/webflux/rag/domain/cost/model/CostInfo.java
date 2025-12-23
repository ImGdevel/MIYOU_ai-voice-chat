package com.study.webflux.rag.domain.cost.model;

public record CostInfo(
	long llmCredits,
	long ttsCredits,
	long totalCredits
) {
	public static CostInfo of(long llmCredits, long ttsCredits) {
		return new CostInfo(llmCredits, ttsCredits, llmCredits + ttsCredits);
	}

	public static CostInfo zero() {
		return new CostInfo(0, 0, 0);
	}
}
