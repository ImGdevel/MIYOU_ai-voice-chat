package com.study.webflux.rag.domain.model.metrics;

import java.time.Instant;
import java.util.List;

public record UsageAnalytics(
	String pipelineId,
	String status,
	Instant timestamp,
	UserRequest userRequest,
	LlmUsage llmUsage,
	RetrievalMetrics retrievalMetrics,
	TtsMetrics ttsMetrics,
	ResponseMetrics responseMetrics
) {
	public record UserRequest(
		String inputText,
		int inputLength,
		String inputPreview) {
	}

	public record LlmUsage(
		String model,
		int tokenCount,
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
		long synthesisTimeMillis) {
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

		public UsageAnalytics build() {
			return new UsageAnalytics(
				pipelineId,
				status,
				timestamp,
				userRequest,
				llmUsage,
				retrievalMetrics,
				ttsMetrics,
				responseMetrics);
		}
	}
}
