package com.study.webflux.rag.application.monitoring.monitor;

import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.study.webflux.rag.domain.monitoring.model.DialoguePipelineStage;
import com.study.webflux.rag.domain.monitoring.model.PerformanceMetrics;
import com.study.webflux.rag.domain.monitoring.model.UsageAnalytics;
import com.study.webflux.rag.domain.monitoring.port.PerformanceMetricsRepository;
import com.study.webflux.rag.domain.monitoring.port.UsageAnalyticsRepository;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class PersistentPipelineMetricsReporter implements PipelineMetricsReporter {

	private final PerformanceMetricsRepository performanceMetricsRepository;
	private final UsageAnalyticsRepository usageAnalyticsRepository;
	private final LoggingPipelineMetricsReporter loggingReporter;

	@Override
	public void report(DialoguePipelineTracker.PipelineSummary summary) {
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

	/**
	 * 파이프라인 요약 정보를 성능 메트릭으로 변환해 저장합니다.
	 */
	private Mono<PerformanceMetrics> savePerformanceMetrics(
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

	/**
	 * 파이프라인 요약 정보를 사용량 분석 모델로 변환해 저장합니다.
	 */
	private Mono<UsageAnalytics> saveUsageAnalytics(
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

	/**
	 * LLM 단계 속성에서 토큰/모델 정보를 추출해 사용량 모델로 변환합니다.
	 */
	private UsageAnalytics.LlmUsage extractLlmUsage(
		DialoguePipelineTracker.PipelineSummary summary) {
		Optional<DialoguePipelineTracker.StageSnapshot> llmStage = findStage(summary,
			DialoguePipelineStage.LLM_COMPLETION);
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

	/**
	 * 메모리 조회/문서 검색 단계의 건수와 수행시간을 합산합니다.
	 */
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

	/**
	 * 문장 조립/TTS 단계의 집계값을 사용해 음성 합성 메트릭을 구성합니다.
	 */
	private UsageAnalytics.TtsMetrics extractTtsMetrics(
		DialoguePipelineTracker.PipelineSummary summary) {
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

	private Optional<DialoguePipelineTracker.StageSnapshot> findStage(
		DialoguePipelineTracker.PipelineSummary summary,
		DialoguePipelineStage stage) {
		return summary.stages().stream().filter(snapshot -> snapshot.stage() == stage).findFirst();
	}

	private <T> void subscribeWithLogging(Mono<T> source,
		String label,
		String pipelineId,
		java.util.function.Function<T, String> idExtractor) {
		source.subscribe(
			result -> log.debug("{} saved: {}", label, idExtractor.apply(result)),
			error -> log.error("Failed to save {} for {}: {}",
				label,
				pipelineId,
				error.getMessage()));
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
