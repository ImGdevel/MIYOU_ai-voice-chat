package com.study.webflux.rag.infrastructure.monitoring.config;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 대화 관련 메트릭을 제공합니다.
 *
 * <p>
 * Phase 1C: LLM & Conversation Metrics - 사용자별 대화 횟수 증가 - 대화 길이 분포 (query/response) - 대화 턴 증가 패턴
 */
@Component
public class ConversationMetricsConfiguration {

	private final MeterRegistry meterRegistry;

	// Counters
	private final Counter conversationIncrementCounter;
	private final Counter conversationResetCounter;

	// Distribution Summaries
	private final DistributionSummary queryLengthSummary;
	private final DistributionSummary responseLengthSummary;
	private final DistributionSummary conversationCountSummary;

	public ConversationMetricsConfiguration(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;

		// Conversation increment counter
		this.conversationIncrementCounter = Counter.builder("conversation.increment.count")
			.description("Number of times conversation counter was incremented")
			.register(meterRegistry);

		// Conversation reset counter
		this.conversationResetCounter = Counter.builder("conversation.reset.count")
			.description("Number of times conversation counter was reset")
			.register(meterRegistry);

		// Query length distribution
		this.queryLengthSummary = DistributionSummary.builder("conversation.query.length")
			.description("Distribution of user query lengths in characters")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);

		// Response length distribution
		this.responseLengthSummary = DistributionSummary.builder("conversation.response.length")
			.description("Distribution of assistant response lengths in characters")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);

		// Conversation count distribution
		this.conversationCountSummary = DistributionSummary
			.builder("conversation.count.distribution")
			.description("Distribution of conversation counts per user")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);
	}

	/**
	 * 대화 카운트 증가를 기록합니다.
	 */
	public void recordConversationIncrement() {
		conversationIncrementCounter.increment();
	}

	/**
	 * 대화 카운트 리셋을 기록합니다.
	 */
	public void recordConversationReset() {
		conversationResetCounter.increment();
	}

	/**
	 * 질의 길이를 기록합니다.
	 */
	public void recordQueryLength(int length) {
		queryLengthSummary.record(length);
	}

	/**
	 * 응답 길이를 기록합니다.
	 */
	public void recordResponseLength(int length) {
		responseLengthSummary.record(length);
	}

	/**
	 * 현재 대화 카운트를 기록합니다.
	 */
	public void recordConversationCount(long count) {
		conversationCountSummary.record(count);
	}

	/**
	 * 대화 페르소나 또는 타입별 카운트를 기록합니다.
	 */
	public void recordConversationByType(String type) {
		Counter.builder("conversation.by_type")
			.tag("type", type)
			.description("Number of conversations by type")
			.register(meterRegistry)
			.increment();
	}
}
