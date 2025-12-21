package com.study.webflux.rag.domain.service;

import com.study.webflux.rag.domain.model.cost.CostInfo;
import com.study.webflux.rag.domain.model.cost.ModelPricing;
import com.study.webflux.rag.domain.model.metrics.UsageAnalytics;

public class CostCalculationService {

	public static CostInfo calculateCost(UsageAnalytics analytics) {
		if (analytics == null) {
			return CostInfo.zero();
		}

		long llmCredits = calculateLlmCredits(analytics);
		long ttsCredits = calculateTtsCredits(analytics);

		return CostInfo.of(llmCredits, ttsCredits);
	}

	private static long calculateLlmCredits(UsageAnalytics analytics) {
		if (analytics.llmUsage() == null) {
			return 0;
		}

		String model = analytics.llmUsage().model();
		int tokenCount = analytics.llmUsage().tokenCount();

		int estimatedPromptTokens = estimatePromptTokens(analytics);
		int completionTokens = tokenCount;

		return ModelPricing.calculateLlmCredits(model, estimatedPromptTokens, completionTokens);
	}

	private static int estimatePromptTokens(UsageAnalytics analytics) {
		if (analytics.userRequest() == null) {
			return 0;
		}

		int inputLength = analytics.userRequest().inputLength();
		int memoryCount = analytics.retrievalMetrics() != null
			? analytics.retrievalMetrics().memoryCount()
			: 0;
		int documentCount = analytics.retrievalMetrics() != null
			? analytics.retrievalMetrics().documentCount()
			: 0;

		int basePromptTokens = 300;
		int inputTokens = inputLength / 3;
		int contextTokens = (memoryCount * 50) + (documentCount * 100);

		return basePromptTokens + inputTokens + contextTokens;
	}

	private static long calculateTtsCredits(UsageAnalytics analytics) {
		if (analytics.ttsMetrics() == null) {
			return 0;
		}

		int sentenceCount = analytics.ttsMetrics().sentenceCount();

		long estimatedAudioLength = estimateAudioLength(sentenceCount);

		return ModelPricing.calculateTtsCredits(estimatedAudioLength);
	}

	private static long estimateAudioLength(int sentenceCount) {
		long avgSentenceDuration = 3000;
		return sentenceCount * avgSentenceDuration;
	}
}
