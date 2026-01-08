package com.study.webflux.rag.domain.cost.model;

/** LLM/TTS와 전체 크레딧 비용을 포함한 대화 비용 정보입니다. */
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
