package com.study.webflux.rag.infrastructure.monitoring.config;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * LLM 호출 품질 및 성능 메트릭을 제공합니다.
 *
 * <p>
 * Phase 1C: LLM & Conversation Metrics - LLM 호출 성공/실패율 - 모델별 응답 시간 분포 - 프롬프트 길이 분포 - 완성 길이 분포
 */
@Component
public class LlmMetricsConfiguration {

	private final MeterRegistry meterRegistry;

	// Counters
	private final Counter llmRequestCounter;
	private final Counter llmSuccessCounter;
	private final Counter llmFailureCounter;

	// Distribution Summaries
	private final DistributionSummary promptLengthSummary;
	private final DistributionSummary completionLengthSummary;
	private final DistributionSummary llmResponseTimeSummary;

	public LlmMetricsConfiguration(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;

		// LLM request counter
		this.llmRequestCounter = Counter.builder("llm.request.count")
			.description("Total number of LLM requests")
			.register(meterRegistry);

		// LLM success counter
		this.llmSuccessCounter = Counter.builder("llm.request.success")
			.description("Number of successful LLM requests")
			.register(meterRegistry);

		// LLM failure counter
		this.llmFailureCounter = Counter.builder("llm.request.failure")
			.description("Number of failed LLM requests")
			.register(meterRegistry);

		// Prompt length distribution
		this.promptLengthSummary = DistributionSummary.builder("llm.prompt.length")
			.description("Distribution of prompt token lengths")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);

		// Completion length distribution
		this.completionLengthSummary = DistributionSummary.builder("llm.completion.length")
			.description("Distribution of completion token lengths")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);

		// LLM response time distribution
		this.llmResponseTimeSummary = DistributionSummary.builder("llm.response.time.ms")
			.description("Distribution of LLM response times in milliseconds")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);
	}

	/**
	 * LLM 요청이 시작되었음을 기록합니다.
	 */
	public void recordLlmRequest() {
		llmRequestCounter.increment();
	}

	/**
	 * LLM 요청이 성공했음을 기록합니다.
	 */
	public void recordLlmSuccess(String model) {
		llmSuccessCounter.increment();
		Counter.builder("llm.success.by_model")
			.tag("model", model)
			.description("Number of successful LLM requests by model")
			.register(meterRegistry)
			.increment();
	}

	/**
	 * LLM 요청이 실패했음을 기록합니다.
	 */
	public void recordLlmFailure(String model, String errorType) {
		llmFailureCounter.increment();
		Counter.builder("llm.failure.by_model")
			.tag("model", model)
			.tag("error_type", errorType)
			.description("Number of failed LLM requests by model and error type")
			.register(meterRegistry)
			.increment();
	}

	/**
	 * 프롬프트 토큰 길이를 기록합니다.
	 */
	public void recordPromptLength(int tokens) {
		promptLengthSummary.record(tokens);
	}

	/**
	 * 완성 토큰 길이를 기록합니다.
	 */
	public void recordCompletionLength(int tokens) {
		completionLengthSummary.record(tokens);
	}

	/**
	 * LLM 응답 시간을 기록합니다.
	 */
	public void recordResponseTime(long milliseconds) {
		llmResponseTimeSummary.record(milliseconds);
	}

	/**
	 * 모델별 응답 시간을 기록합니다.
	 */
	public void recordResponseTimeByModel(String model, long milliseconds) {
		DistributionSummary.builder("llm.response.time.by_model")
			.tag("model", model)
			.description("LLM response time distribution by model")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry)
			.record(milliseconds);
	}
}
