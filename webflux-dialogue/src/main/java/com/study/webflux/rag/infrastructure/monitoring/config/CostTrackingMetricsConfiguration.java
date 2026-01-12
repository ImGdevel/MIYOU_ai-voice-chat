package com.study.webflux.rag.infrastructure.monitoring.config;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * LLM 및 TTS 비용 추적을 위한 메트릭 설정.
 *
 * <p>
 * 제공 메트릭:
 * <ul>
 * <li>llm.cost.usd.total - LLM 누적 비용 (USD)</li>
 * <li>llm.cost.usd.daily - LLM 일일 비용 (USD)</li>
 * <li>llm.cost.usd.monthly - LLM 월별 비용 (USD)</li>
 * <li>llm.cost.by_model - 모델별 비용 (USD, tag: model)</li>
 * <li>llm.cost.by_user - 사용자별 비용 (USD, tags: user_id, model)</li>
 * <li>tts.cost.usd.total - TTS 누적 비용 (USD)</li>
 * <li>tts.cost.usd.daily - TTS 일일 비용 (USD)</li>
 * <li>tts.cost.usd.monthly - TTS 월별 비용 (USD)</li>
 * <li>cost.budget.remaining - 남은 예산 (USD, tag: budget_type)</li>
 * </ul>
 *
 * <p>
 * 사용 예시:
 *
 * <pre>{@code
 * costMetrics.recordLlmCost("gpt-4o", 1000, 500);
 * costMetrics.recordUserLlmCost("user123", "gpt-4o", 0.015);
 * costMetrics.recordTtsCost("supertone", 1500);
 * }</pre>
 */
@Component
public class CostTrackingMetricsConfiguration {

	private final MeterRegistry meterRegistry;

	// LLM 비용
	private final Counter llmCostTotal;
	private final AtomicReference<Double> llmCostDaily;
	private final AtomicReference<Double> llmCostMonthly;

	// TTS 비용
	private final Counter ttsCostTotal;
	private final AtomicReference<Double> ttsCostDaily;
	private final AtomicReference<Double> ttsCostMonthly;

	// 예산 추적
	private final AtomicReference<Double> budgetRemaining;

	public CostTrackingMetricsConfiguration(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;

		// LLM Counter 등록
		this.llmCostTotal = Counter.builder("llm.cost.usd.total")
			.description("Total LLM cost in USD")
			.baseUnit("usd")
			.register(meterRegistry);

		// LLM Gauge 등록
		this.llmCostDaily = new AtomicReference<>(0.0);
		Gauge.builder("llm.cost.usd.daily", llmCostDaily, AtomicReference::get)
			.description("Daily LLM cost in USD")
			.baseUnit("usd")
			.register(meterRegistry);

		this.llmCostMonthly = new AtomicReference<>(0.0);
		Gauge.builder("llm.cost.usd.monthly", llmCostMonthly, AtomicReference::get)
			.description("Monthly LLM cost in USD")
			.baseUnit("usd")
			.register(meterRegistry);

		// TTS Counter 등록
		this.ttsCostTotal = Counter.builder("tts.cost.usd.total")
			.description("Total TTS cost in USD")
			.baseUnit("usd")
			.register(meterRegistry);

		// TTS Gauge 등록
		this.ttsCostDaily = new AtomicReference<>(0.0);
		Gauge.builder("tts.cost.usd.daily", ttsCostDaily, AtomicReference::get)
			.description("Daily TTS cost in USD")
			.baseUnit("usd")
			.register(meterRegistry);

		this.ttsCostMonthly = new AtomicReference<>(0.0);
		Gauge.builder("tts.cost.usd.monthly", ttsCostMonthly, AtomicReference::get)
			.description("Monthly TTS cost in USD")
			.baseUnit("usd")
			.register(meterRegistry);

		// 예산 Gauge 등록
		this.budgetRemaining = new AtomicReference<>(1000.0); // 기본 예산 $1000
		Gauge.builder("cost.budget.remaining", budgetRemaining, AtomicReference::get)
			.description("Remaining budget in USD")
			.baseUnit("usd")
			.tag("budget_type", "monthly")
			.register(meterRegistry);
	}

	/**
	 * LLM 비용 기록.
	 *
	 * @param model
	 *            LLM 모델명 (예: "gpt-4o", "gpt-4o-mini")
	 * @param promptTokens
	 *            프롬프트 토큰 수
	 * @param completionTokens
	 *            완성 토큰 수
	 */
	public void recordLlmCost(String model, int promptTokens, int completionTokens) {
		double cost = calculateLlmCost(model, promptTokens, completionTokens);

		// 누적 비용
		llmCostTotal.increment(cost);

		// 모델별 비용
		Counter.builder("llm.cost.by_model")
			.description("LLM cost by model")
			.baseUnit("usd")
			.tag("model", model)
			.register(meterRegistry)
			.increment(cost);

		// 일일/월별 비용 업데이트
		llmCostDaily.updateAndGet(current -> current + cost);
		llmCostMonthly.updateAndGet(current -> current + cost);

		// 예산 차감
		budgetRemaining.updateAndGet(current -> Math.max(0, current - cost));
	}

	/**
	 * 사용자별 LLM 비용 기록.
	 *
	 * @param userId
	 *            사용자 ID
	 * @param model
	 *            LLM 모델명
	 * @param cost
	 *            비용 (USD)
	 */
	public void recordUserLlmCost(String userId, String model, double cost) {
		Counter.builder("llm.cost.by_user")
			.description("LLM cost by user")
			.baseUnit("usd")
			.tag("user_id", userId)
			.tag("model", model)
			.register(meterRegistry)
			.increment(cost);
	}

	/**
	 * TTS 비용 기록.
	 *
	 * @param provider
	 *            TTS 제공자 (예: "supertone")
	 * @param characters
	 *            문자 수
	 */
	public void recordTtsCost(String provider, int characters) {
		double cost = calculateTtsCost(provider, characters);

		// 누적 비용
		ttsCostTotal.increment(cost);

		// 제공자별 비용
		Counter.builder("tts.cost.by_provider")
			.description("TTS cost by provider")
			.baseUnit("usd")
			.tag("provider", provider)
			.register(meterRegistry)
			.increment(cost);

		// 일일/월별 비용 업데이트
		ttsCostDaily.updateAndGet(current -> current + cost);
		ttsCostMonthly.updateAndGet(current -> current + cost);

		// 예산 차감
		budgetRemaining.updateAndGet(current -> Math.max(0, current - cost));
	}

	/**
	 * 일일 비용 리셋 (매일 자정 스케줄러에서 호출).
	 */
	public void resetDailyCost() {
		llmCostDaily.set(0.0);
		ttsCostDaily.set(0.0);
	}

	/**
	 * 월별 비용 리셋 (매월 1일 스케줄러에서 호출).
	 */
	public void resetMonthlyCost() {
		llmCostMonthly.set(0.0);
		ttsCostMonthly.set(0.0);
		budgetRemaining.set(1000.0); // 예산 재설정
	}

	/**
	 * 예산 업데이트.
	 *
	 * @param budget
	 *            새로운 예산 (USD)
	 */
	public void updateBudget(double budget) {
		budgetRemaining.set(budget);
	}

	/**
	 * LLM 비용 계산 (2026년 1월 기준 가격).
	 *
	 * @param model
	 *            LLM 모델명
	 * @param promptTokens
	 *            프롬프트 토큰 수
	 * @param completionTokens
	 *            완성 토큰 수
	 * @return 비용 (USD)
	 */
	private double calculateLlmCost(String model, int promptTokens, int completionTokens) {
		String modelLower = model.toLowerCase();

		// GPT-4o
		if (modelLower.contains("gpt-4o") && !modelLower.contains("mini")) {
			double promptCost = (promptTokens / 1_000_000.0) * 2.50;
			double completionCost = (completionTokens / 1_000_000.0) * 10.00;
			return promptCost + completionCost;
		}

		// GPT-4o-mini
		if (modelLower.contains("gpt-4o-mini")) {
			double promptCost = (promptTokens / 1_000_000.0) * 0.150;
			double completionCost = (completionTokens / 1_000_000.0) * 0.600;
			return promptCost + completionCost;
		}

		// GPT-4-turbo
		if (modelLower.contains("gpt-4-turbo")) {
			double promptCost = (promptTokens / 1_000_000.0) * 10.00;
			double completionCost = (completionTokens / 1_000_000.0) * 30.00;
			return promptCost + completionCost;
		}

		// GPT-3.5-turbo
		if (modelLower.contains("gpt-3.5-turbo")) {
			double promptCost = (promptTokens / 1_000_000.0) * 0.50;
			double completionCost = (completionTokens / 1_000_000.0) * 1.50;
			return promptCost + completionCost;
		}

		// 기본값 (GPT-4o 가격 사용)
		return ((promptTokens + completionTokens) / 1_000_000.0) * 5.0;
	}

	/**
	 * TTS 비용 계산 (추정 가격).
	 *
	 * @param provider
	 *            TTS 제공자
	 * @param characters
	 *            문자 수
	 * @return 비용 (USD)
	 */
	private double calculateTtsCost(String provider, int characters) {
		String providerLower = provider.toLowerCase();

		// Supertone (추정 가격: $0.015 per 1K characters)
		if (providerLower.contains("supertone")) {
			return (characters / 1_000.0) * 0.015;
		}

		// OpenAI TTS (실제 가격: $0.015 per 1K characters for tts-1)
		if (providerLower.contains("openai") || providerLower.contains("tts-1")) {
			return (characters / 1_000.0) * 0.015;
		}

		// 기본값
		return (characters / 1_000.0) * 0.01;
	}
}
