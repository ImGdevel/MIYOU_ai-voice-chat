package com.study.webflux.rag.domain.model.cost;

import java.util.Map;

public class ModelPricing {
	private static final double CREDITS_PER_DOLLAR = 10000.0;

	private static final Map<String, PricePerMillion> LLM_PRICES = Map.of(
		"gpt-4o-mini",
		new PricePerMillion(0.150, 0.600),
		"gpt-4o",
		new PricePerMillion(2.50, 10.00),
		"gpt-4-turbo",
		new PricePerMillion(10.00, 30.00),
		"gpt-3.5-turbo",
		new PricePerMillion(0.50, 1.50));

	private static final double TTS_PRICE_PER_100MS = 0.00015;

	public static long calculateLlmCredits(String model, int promptTokens, int completionTokens) {
		PricePerMillion price = LLM_PRICES.getOrDefault(model, new PricePerMillion(0.150, 0.600));

		double inputCost = (promptTokens / 1_000_000.0) * price.inputPrice;
		double outputCost = (completionTokens / 1_000_000.0) * price.outputPrice;
		double totalDollars = inputCost + outputCost;

		return (long) Math.ceil(totalDollars * CREDITS_PER_DOLLAR);
	}

	public static long calculateTtsCredits(long audioLengthMillis) {
		double totalCost = (audioLengthMillis / 100.0) * TTS_PRICE_PER_100MS;
		return (long) Math.ceil(totalCost * CREDITS_PER_DOLLAR);
	}

	private record PricePerMillion(
		double inputPrice,
		double outputPrice) {
	}
}
