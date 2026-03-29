package com.miyou.app.infrastructure.monitoring.document;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.miyou.app.domain.cost.model.CostInfo;
import com.miyou.app.domain.monitoring.model.UsageAnalytics;

@Document(collection = "usage_analytics")
@CompoundIndex(name = "timestamp_model", def = "{'timestamp': -1, 'llmUsage.model': 1}")
public record UsageAnalyticsDocument(
	@Id String pipelineId,
	String status,
	@Indexed Instant timestamp,
	UserRequestDoc userRequest,
	LlmUsageDoc llmUsage,
	RetrievalMetricsDoc retrievalMetrics,
	TtsMetricsDoc ttsMetrics,
	ResponseMetricsDoc responseMetrics,
	CostInfoDoc costInfo
) {
	public record UserRequestDoc(
		String inputText,
		int inputLength,
		String inputPreview) {
		public static UserRequestDoc fromDomain(UsageAnalytics.UserRequest domain) {
			return new UserRequestDoc(
				domain.inputText(),
				domain.inputLength(),
				domain.inputPreview());
		}

		public UsageAnalytics.UserRequest toDomain() {
			return new UsageAnalytics.UserRequest(inputText, inputLength, inputPreview);
		}
	}

	public record LlmUsageDoc(
		@Indexed String model,
		int promptTokens,
		int completionTokens,
		@Indexed int totalTokens,
		List<String> generatedSentences,
		long completionTimeMillis) {
		public static LlmUsageDoc fromDomain(UsageAnalytics.LlmUsage domain) {
			return new LlmUsageDoc(
				domain.model(),
				domain.promptTokens(),
				domain.completionTokens(),
				domain.totalTokens(),
				domain.generatedSentences(),
				domain.completionTimeMillis());
		}

		public UsageAnalytics.LlmUsage toDomain() {
			return new UsageAnalytics.LlmUsage(
				model,
				promptTokens,
				completionTokens,
				totalTokens,
				generatedSentences,
				completionTimeMillis);
		}
	}

	public record RetrievalMetricsDoc(
		int memoryCount,
		int documentCount,
		long retrievalTimeMillis) {
		public static RetrievalMetricsDoc fromDomain(UsageAnalytics.RetrievalMetrics domain) {
			return new RetrievalMetricsDoc(
				domain.memoryCount(),
				domain.documentCount(),
				domain.retrievalTimeMillis());
		}

		public UsageAnalytics.RetrievalMetrics toDomain() {
			return new UsageAnalytics.RetrievalMetrics(memoryCount, documentCount,
				retrievalTimeMillis);
		}
	}

	public record TtsMetricsDoc(
		int sentenceCount,
		int audioChunks,
		long synthesisTimeMillis,
		long audioLengthMillis) {
		public static TtsMetricsDoc fromDomain(UsageAnalytics.TtsMetrics domain) {
			return new TtsMetricsDoc(
				domain.sentenceCount(),
				domain.audioChunks(),
				domain.synthesisTimeMillis(),
				domain.audioLengthMillis());
		}

		public UsageAnalytics.TtsMetrics toDomain() {
			return new UsageAnalytics.TtsMetrics(sentenceCount, audioChunks, synthesisTimeMillis,
				audioLengthMillis);
		}
	}

	public record ResponseMetricsDoc(
		long totalDurationMillis,
		Long firstResponseLatencyMillis,
		Long lastResponseLatencyMillis) {
		public static ResponseMetricsDoc fromDomain(UsageAnalytics.ResponseMetrics domain) {
			return new ResponseMetricsDoc(
				domain.totalDurationMillis(),
				domain.firstResponseLatencyMillis(),
				domain.lastResponseLatencyMillis());
		}

		public UsageAnalytics.ResponseMetrics toDomain() {
			return new UsageAnalytics.ResponseMetrics(
				totalDurationMillis,
				firstResponseLatencyMillis,
				lastResponseLatencyMillis);
		}
	}

	public record CostInfoDoc(
		long llmCredits,
		long ttsCredits,
		long totalCredits) {
		public static CostInfoDoc fromDomain(CostInfo domain) {
			return new CostInfoDoc(
				domain.llmCredits(),
				domain.ttsCredits(),
				domain.totalCredits());
		}

		public CostInfo toDomain() {
			return new CostInfo(llmCredits, ttsCredits, totalCredits);
		}
	}

	public static UsageAnalyticsDocument fromDomain(UsageAnalytics domain) {
		return new UsageAnalyticsDocument(
			domain.pipelineId(),
			domain.status(),
			domain.timestamp(),
			domain.userRequest() != null
				? UserRequestDoc.fromDomain(domain.userRequest())
				: null,
			domain.llmUsage() != null ? LlmUsageDoc.fromDomain(domain.llmUsage()) : null,
			domain.retrievalMetrics() != null
				? RetrievalMetricsDoc.fromDomain(domain.retrievalMetrics())
				: null,
			domain.ttsMetrics() != null ? TtsMetricsDoc.fromDomain(domain.ttsMetrics()) : null,
			domain.responseMetrics() != null
				? ResponseMetricsDoc.fromDomain(domain.responseMetrics())
				: null,
			domain.costInfo() != null ? CostInfoDoc.fromDomain(domain.costInfo()) : null);
	}

	public UsageAnalytics toDomain() {
		return new UsageAnalytics(
			pipelineId,
			status,
			timestamp,
			userRequest != null ? userRequest.toDomain() : null,
			llmUsage != null ? llmUsage.toDomain() : null,
			retrievalMetrics != null ? retrievalMetrics.toDomain() : null,
			ttsMetrics != null ? ttsMetrics.toDomain() : null,
			responseMetrics != null ? responseMetrics.toDomain() : null,
			costInfo != null ? costInfo.toDomain() : null);
	}
}
