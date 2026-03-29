package com.miyou.app.infrastructure.outbound.monitoring;

import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.miyou.app.domain.monitoring.model.DialoguePipelineStage;
import com.miyou.app.domain.monitoring.model.PerformanceMetrics;
import com.miyou.app.domain.monitoring.model.PipelineSummary;
import com.miyou.app.domain.monitoring.model.StageSnapshot;
import com.miyou.app.domain.monitoring.model.UsageAnalytics;
import com.miyou.app.domain.monitoring.port.PerformanceMetricsRepository;
import com.miyou.app.domain.monitoring.port.PipelineMetricsReporter;
import com.miyou.app.domain.monitoring.port.UsageAnalyticsRepository;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class PersistentPipelineMetricsReporter implements PipelineMetricsReporter {

	private final PerformanceMetricsRepository performanceMetricsRepository;
	private final UsageAnalyticsRepository usageAnalyticsRepository;
	private final LoggingPipelineMetricsReporter loggingReporter;

	@Override
	public void report(PipelineSummary summary) {
		loggingReporter.report(summary);

		subscribeWithLogging(savePerformanceMetrics(summary),
			"performance metrics",
			summary.pipelineId(),
			PerformanceMetrics::pipelineId);
		subscribeWithLogging(saveUsageAnalytics(summary),
			"usage analytics",
			summary.pipelineId(),
			UsageAnalytics::pipelineId);
	}

	private Mono<PerformanceMetrics> savePerformanceMetrics(PipelineSummary summary) {
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

	private Mono<UsageAnalytics> saveUsageAnalytics(PipelineSummary summary) {
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

	private UsageAnalytics.LlmUsage extractLlmUsage(PipelineSummary summary) {
		Optional<StageSnapshot> llmStage = findStage(summary, DialoguePipelineStage.LLM_COMPLETION);
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
			asInt(promptTokens),
			asInt(completionTokens),
			totalTokens,
			summary.llmOutputs(),
			llmStage.get().durationMillis());
	}

	private UsageAnalytics.RetrievalMetrics extractRetrievalMetrics(PipelineSummary summary) {
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

	private UsageAnalytics.TtsMetrics extractTtsMetrics(PipelineSummary summary) {
		int sentenceCount = 0;
		int audioChunks = 0;
		long synthesisTime = 0;
		long audioLengthMillis = 0;

		for (var stage : summary.stages()) {
			if (stage.stage() == DialoguePipelineStage.SENTENCE_ASSEMBLY) {
				sentenceCount = extractInt(stage.attributes(), "sentenceCount");
			} else if (stage.stage() == DialoguePipelineStage.TTS_SYNTHESIS) {
				audioChunks = extractInt(stage.attributes(), "audioChunks");
				synthesisTime = stage.durationMillis();
				audioLengthMillis = extractLong(stage.attributes(), "audioLengthMillis");
			}
		}

		if (audioLengthMillis == 0) {
			audioLengthMillis = synthesisTime;
		}
		return new UsageAnalytics.TtsMetrics(sentenceCount, audioChunks, synthesisTime,
			audioLengthMillis);
	}

	private int asInt(Integer value) {
		return value != null ? value : 0;
	}

	private long extractLong(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return 0L;
	}

	private Optional<StageSnapshot> findStage(PipelineSummary summary,
		DialoguePipelineStage stage) {
		return summary.stages().stream().filter(snapshot -> snapshot.stage() == stage).findFirst();
	}

	private <T> void subscribeWithLogging(Mono<T> source,
		String label,
		String pipelineId,
		java.util.function.Function<T, String> idExtractor) {
		source.subscribe(
			result -> log.debug("{} saved: {}", label, idExtractor.apply(result)),
			error -> log.error("Failed to save {} for {}", label, pipelineId, error));
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
