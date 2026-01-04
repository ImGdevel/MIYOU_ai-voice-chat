package com.study.webflux.rag.application.monitoring.monitor;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.study.webflux.rag.domain.monitoring.model.DialoguePipelineStage;
import com.study.webflux.rag.domain.monitoring.model.PerformanceMetrics;
import com.study.webflux.rag.domain.monitoring.model.UsageAnalytics;
import com.study.webflux.rag.domain.monitoring.port.PerformanceMetricsRepository;
import com.study.webflux.rag.domain.monitoring.port.UsageAnalyticsRepository;

@Slf4j
@RequiredArgsConstructor
public class PersistentPipelineMetricsReporter implements PipelineMetricsReporter {

	private final PerformanceMetricsRepository performanceMetricsRepository;
	private final UsageAnalyticsRepository usageAnalyticsRepository;
	private final LoggingPipelineMetricsReporter loggingReporter;

	@Override
	public void report(DialoguePipelineTracker.PipelineSummary summary) {
		loggingReporter.report(summary);

		savePerformanceMetrics(summary).subscribe(
			metrics -> log.debug("Performance metrics saved: {}", metrics.pipelineId()),
			error -> log.error("Failed to save performance metrics for {}: {}",
				summary.pipelineId(),
				error.getMessage()));

		saveUsageAnalytics(summary).subscribe(
			analytics -> log.debug("Usage analytics saved: {}", analytics.pipelineId()),
			error -> log.error("Failed to save usage analytics for {}: {}",
				summary.pipelineId(),
				error.getMessage()));
	}

	private reactor.core.publisher.Mono<PerformanceMetrics> savePerformanceMetrics(
		DialoguePipelineTracker.PipelineSummary summary) {
		PerformanceMetrics metrics = PerformanceMetrics.fromPipelineSummary(
			summary.pipelineId(),
			summary.status().toString(),
			summary.startedAt(),
			summary.finishedAt(),
			summary.durationMillis(),
			summary.firstResponseLatencyMillis(),
			summary.lastResponseLatencyMillis(),
			summary.stages().stream()
				.map(stage -> new PerformanceMetrics.StagePerformance(
					stage.stage().toString(),
					stage.status().toString(),
					stage.startedAt(),
					stage.finishedAt(),
					stage.durationMillis(),
					stage.attributes()))
				.toList(),
			summary.attributes());

		return performanceMetricsRepository.save(metrics);
	}

	private reactor.core.publisher.Mono<UsageAnalytics> saveUsageAnalytics(
		DialoguePipelineTracker.PipelineSummary summary) {
		Map<String, Object> attrs = summary.attributes();

		String inputPreview = extractString(attrs, "input.preview");
		String inputText = extractString(attrs, "input.text");
		if (inputText == null || inputText.isBlank()) {
			inputText = inputPreview;
		}

		UsageAnalytics.UserRequest userRequest = new UsageAnalytics.UserRequest(
			inputText,
			extractInt(attrs, "input.length"),
			inputPreview);

		UsageAnalytics.LlmUsage llmUsage = extractLlmUsage(summary);
		UsageAnalytics.RetrievalMetrics retrievalMetrics = extractRetrievalMetrics(summary);
		UsageAnalytics.TtsMetrics ttsMetrics = extractTtsMetrics(summary);

		UsageAnalytics.ResponseMetrics responseMetrics = new UsageAnalytics.ResponseMetrics(
			summary.durationMillis(),
			summary.firstResponseLatencyMillis(),
			summary.lastResponseLatencyMillis());

		UsageAnalytics analytics = UsageAnalytics.builder()
			.pipelineId(summary.pipelineId())
			.status(summary.status().toString())
			.timestamp(summary.finishedAt())
			.userRequest(userRequest)
			.llmUsage(llmUsage)
			.retrievalMetrics(retrievalMetrics)
			.ttsMetrics(ttsMetrics)
			.responseMetrics(responseMetrics)
			.build();

		return usageAnalyticsRepository.save(analytics);
	}

	private UsageAnalytics.LlmUsage extractLlmUsage(
		DialoguePipelineTracker.PipelineSummary summary) {
		var llmStage = summary.stages().stream()
			.filter(s -> s.stage() == DialoguePipelineStage.LLM_COMPLETION)
			.findFirst();

		if (llmStage.isEmpty()) {
			return null;
		}

		Map<String, Object> attrs = llmStage.get().attributes();

		int totalTokens = extractInt(attrs, "totalTokens");
		if (totalTokens == 0) {
			totalTokens = extractInt(attrs, "tokenCount");
		}

		Integer promptTokens = extractIntOrNull(attrs, "promptTokens");
		Integer completionTokens = extractIntOrNull(attrs, "completionTokens");

		return new UsageAnalytics.LlmUsage(
			extractString(attrs, "model"),
			totalTokens,
			promptTokens,
			completionTokens,
			summary.llmOutputs(),
			llmStage.get().durationMillis());
	}

	private UsageAnalytics.RetrievalMetrics extractRetrievalMetrics(
		DialoguePipelineTracker.PipelineSummary summary) {
		int memoryCount = 0;
		int documentCount = 0;
		long retrievalTime = 0;

		for (var stage : summary.stages()) {
			if (stage.stage() == DialoguePipelineStage.MEMORY_RETRIEVAL) {
				memoryCount = extractInt(stage.attributes(), "memoryCount");
				retrievalTime += stage.durationMillis();
			} else if (stage.stage() == DialoguePipelineStage.RETRIEVAL) {
				documentCount = extractInt(stage.attributes(), "documentCount");
				retrievalTime += stage.durationMillis();
			}
		}

		return new UsageAnalytics.RetrievalMetrics(memoryCount, documentCount, retrievalTime);
	}

	private UsageAnalytics.TtsMetrics extractTtsMetrics(
		DialoguePipelineTracker.PipelineSummary summary) {
		int sentenceCount = 0;
		int audioChunks = 0;
		long synthesisTime = 0;

		for (var stage : summary.stages()) {
			if (stage.stage() == DialoguePipelineStage.SENTENCE_ASSEMBLY) {
				sentenceCount = extractInt(stage.attributes(), "sentenceCount");
			} else if (stage.stage() == DialoguePipelineStage.TTS_SYNTHESIS) {
				audioChunks = extractInt(stage.attributes(), "audioChunks");
				synthesisTime = stage.durationMillis();
			}
		}

		return new UsageAnalytics.TtsMetrics(sentenceCount, audioChunks, synthesisTime);
	}

	private String extractString(Map<String, Object> map, String key) {
		Object value = map.get(key);
		return value != null ? value.toString() : "";
	}

	private int extractInt(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return 0;
	}

	private Integer extractIntOrNull(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return null;
	}
}
