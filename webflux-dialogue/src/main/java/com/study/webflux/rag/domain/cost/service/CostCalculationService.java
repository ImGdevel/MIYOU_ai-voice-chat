package com.study.webflux.rag.domain.cost.service;

import com.study.webflux.rag.domain.cost.model.CostInfo;
import com.study.webflux.rag.domain.cost.model.ModelPricing;
import com.study.webflux.rag.domain.monitoring.model.UsageAnalytics;

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

		Integer actualPromptTokens = analytics.llmUsage().promptTokens();
		Integer actualCompletionTokens = analytics.llmUsage().completionTokens();

		int promptTokens = actualPromptTokens != null
			? actualPromptTokens
			: estimatePromptTokens(analytics);
		int completionTokens = actualCompletionTokens != null
			? actualCompletionTokens
			: analytics.llmUsage().tokenCount();

		return ModelPricing.calculateLlmCredits(model, promptTokens, completionTokens);
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
