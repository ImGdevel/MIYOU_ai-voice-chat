package com.study.webflux.rag.domain.cost.model;

import java.util.Map;

public class ModelPricing {
	private static final double CREDITS_PER_DOLLAR = 10000.0;

	private static final Map<String, PricePerMillion> LLM_PRICES = Map.ofEntries(
		Map.entry("gpt-5.2", new PricePerMillion(1.75, 14.00)),
		Map.entry("gpt-5.1", new PricePerMillion(1.25, 10.00)),
		Map.entry("gpt-5", new PricePerMillion(1.25, 10.00)),
		Map.entry("gpt-5-mini", new PricePerMillion(0.25, 2.00)),
		Map.entry("gpt-5-nano", new PricePerMillion(0.05, 0.40)),
		Map.entry("gpt-4.1", new PricePerMillion(2.00, 8.00)),
		Map.entry("gpt-4.1-mini", new PricePerMillion(0.40, 1.60)),
		Map.entry("gpt-4.1-nano", new PricePerMillion(0.10, 0.40)),
		Map.entry("gpt-4o-2024-05-13", new PricePerMillion(5.00, 15.00)),
		Map.entry("gpt-4o-mini", new PricePerMillion(0.150, 0.600)),
		Map.entry("gpt-4o", new PricePerMillion(2.50, 10.00)),
		Map.entry("gpt-4-turbo", new PricePerMillion(10.00, 30.00)),
		Map.entry("gpt-3.5-turbo", new PricePerMillion(0.50, 1.50)));

	private static final Map<String, EmbeddingPricePerMillion> EMBEDDING_PRICES = Map.of(
		"text-embedding-3-small",
		new EmbeddingPricePerMillion(0.020, 0.010),
		"text-embedding-3-large",
		new EmbeddingPricePerMillion(0.130, 0.065),
		"text-embedding-ada-002",
		new EmbeddingPricePerMillion(0.100, 0.050));

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

	public static long calculateEmbeddingCredits(String model, long tokens, boolean batch) {
		EmbeddingPricePerMillion price = EMBEDDING_PRICES.get(model);
		if (price == null) {
			return 0L;
		}
		double perMillion = batch ? price.batchPrice : price.cost;
		double totalDollars = (tokens / 1_000_000.0) * perMillion;
		return (long) Math.ceil(totalDollars * CREDITS_PER_DOLLAR);
	}

	private record PricePerMillion(
		double inputPrice,
		double outputPrice) {
	}

	private record EmbeddingPricePerMillion(
		double cost,
		double batchPrice) {
	}
}
