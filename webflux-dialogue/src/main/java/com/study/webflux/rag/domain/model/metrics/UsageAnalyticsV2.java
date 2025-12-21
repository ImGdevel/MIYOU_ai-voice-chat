package com.study.webflux.rag.domain.model.metrics;

import java.time.Instant;
import java.util.List;

import com.study.webflux.rag.domain.model.cost.CostInfo;

public record UsageAnalyticsV2(
	String pipelineId,
	String status,
	Instant timestamp,
	UserRequest userRequest,
	LlmUsage llmUsage,
	RetrievalMetrics retrievalMetrics,
	TtsMetrics ttsMetrics,
	ResponseMetrics responseMetrics,
	CostInfo costInfo
) {
	public record UserRequest(
		String inputText,
		int inputLength,
		String inputPreview) {
	}

	public record LlmUsage(
		String model,
		int promptTokens,
		int completionTokens,
		int totalTokens,
		List<String> generatedSentences,
		long completionTimeMillis) {
	}

	public record RetrievalMetrics(
		int memoryCount,
		int documentCount,
		long retrievalTimeMillis) {
	}

	public record TtsMetrics(
		int sentenceCount,
		int audioChunks,
		long synthesisTimeMillis,
		long audioLengthMillis) {
	}

	public record ResponseMetrics(
		long totalDurationMillis,
		Long firstResponseLatencyMillis,
		Long lastResponseLatencyMillis) {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String pipelineId;
		private String status;
		private Instant timestamp;
		private UserRequest userRequest;
		private LlmUsage llmUsage;
		private RetrievalMetrics retrievalMetrics;
		private TtsMetrics ttsMetrics;
		private ResponseMetrics responseMetrics;
		private CostInfo costInfo;

		public Builder pipelineId(String pipelineId) {
			this.pipelineId = pipelineId;
			return this;
		}

		public Builder status(String status) {
			this.status = status;
			return this;
		}

		public Builder timestamp(Instant timestamp) {
			this.timestamp = timestamp;
			return this;
		}

		public Builder userRequest(UserRequest userRequest) {
			this.userRequest = userRequest;
			return this;
		}

		public Builder llmUsage(LlmUsage llmUsage) {
			this.llmUsage = llmUsage;
			return this;
		}

		public Builder retrievalMetrics(RetrievalMetrics retrievalMetrics) {
			this.retrievalMetrics = retrievalMetrics;
			return this;
		}

		public Builder ttsMetrics(TtsMetrics ttsMetrics) {
			this.ttsMetrics = ttsMetrics;
			return this;
		}

		public Builder responseMetrics(ResponseMetrics responseMetrics) {
			this.responseMetrics = responseMetrics;
			return this;
		}

		public Builder costInfo(CostInfo costInfo) {
			this.costInfo = costInfo;
			return this;
		}

		public UsageAnalyticsV2 build() {
			return new UsageAnalyticsV2(
				pipelineId,
				status,
				timestamp,
				userRequest,
				llmUsage,
				retrievalMetrics,
				ttsMetrics,
				responseMetrics,
				costInfo);
		}
	}
}
