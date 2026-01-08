package com.study.webflux.rag.domain.monitoring.entity;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.study.webflux.rag.domain.cost.model.CostInfo;
import com.study.webflux.rag.domain.monitoring.model.UsageAnalytics;

@Document(collection = "usage_analytics")
@CompoundIndex(name = "timestamp_model", def = "{'timestamp': -1, 'llmUsage.model': 1}")
public record UsageAnalyticsEntity(
	@Id String pipelineId,
	String status,
	@Indexed Instant timestamp,
	UserRequestEntity userRequest,
	LlmUsageEntity llmUsage,
	RetrievalMetricsEntity retrievalMetrics,
	TtsMetricsEntity ttsMetrics,
	ResponseMetricsEntity responseMetrics,
	CostInfoEntity costInfo
) {
	public record UserRequestEntity(
		String inputText,
		int inputLength,
		String inputPreview) {
		public static UserRequestEntity fromDomain(UsageAnalytics.UserRequest domain) {
			return new UserRequestEntity(
				domain.inputText(),
				domain.inputLength(),
				domain.inputPreview());
		}

		public UsageAnalytics.UserRequest toDomain() {
			return new UsageAnalytics.UserRequest(inputText, inputLength, inputPreview);
		}
	}

	public record LlmUsageEntity(
		@Indexed String model,
		int promptTokens,
		int completionTokens,
		@Indexed int totalTokens,
		List<String> generatedSentences,
		long completionTimeMillis) {
		public static LlmUsageEntity fromDomain(UsageAnalytics.LlmUsage domain) {
			return new LlmUsageEntity(
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

	public record RetrievalMetricsEntity(
		int memoryCount,
		int documentCount,
		long retrievalTimeMillis) {
		public static RetrievalMetricsEntity fromDomain(UsageAnalytics.RetrievalMetrics domain) {
			return new RetrievalMetricsEntity(
				domain.memoryCount(),
				domain.documentCount(),
				domain.retrievalTimeMillis());
		}

		public UsageAnalytics.RetrievalMetrics toDomain() {
			return new UsageAnalytics.RetrievalMetrics(memoryCount, documentCount,
				retrievalTimeMillis);
		}
	}

	public record TtsMetricsEntity(
		int sentenceCount,
		int audioChunks,
		long synthesisTimeMillis,
		long audioLengthMillis) {
		public static TtsMetricsEntity fromDomain(UsageAnalytics.TtsMetrics domain) {
			return new TtsMetricsEntity(
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

	public record ResponseMetricsEntity(
		long totalDurationMillis,
		Long firstResponseLatencyMillis,
		Long lastResponseLatencyMillis) {
		public static ResponseMetricsEntity fromDomain(UsageAnalytics.ResponseMetrics domain) {
			return new ResponseMetricsEntity(
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

	public record CostInfoEntity(
		long llmCredits,
		long ttsCredits,
		long totalCredits) {
		public static CostInfoEntity fromDomain(CostInfo domain) {
			return new CostInfoEntity(
				domain.llmCredits(),
				domain.ttsCredits(),
				domain.totalCredits());
		}

		public CostInfo toDomain() {
			return new CostInfo(llmCredits, ttsCredits, totalCredits);
		}
	}

	public static UsageAnalyticsEntity fromDomain(UsageAnalytics domain) {
		return new UsageAnalyticsEntity(
			domain.pipelineId(),
			domain.status(),
			domain.timestamp(),
			domain.userRequest() != null
				? UserRequestEntity.fromDomain(domain.userRequest())
				: null,
			domain.llmUsage() != null ? LlmUsageEntity.fromDomain(domain.llmUsage()) : null,
			domain.retrievalMetrics() != null
				? RetrievalMetricsEntity.fromDomain(domain.retrievalMetrics())
				: null,
			domain.ttsMetrics() != null ? TtsMetricsEntity.fromDomain(domain.ttsMetrics()) : null,
			domain.responseMetrics() != null
				? ResponseMetricsEntity.fromDomain(domain.responseMetrics())
				: null,
			domain.costInfo() != null ? CostInfoEntity.fromDomain(domain.costInfo()) : null);
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
