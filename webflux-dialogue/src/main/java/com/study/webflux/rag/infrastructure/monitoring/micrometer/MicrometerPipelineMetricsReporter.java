package com.study.webflux.rag.infrastructure.monitoring.micrometer;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineTracker.PipelineSummary;
import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineTracker.StageSnapshot;
import com.study.webflux.rag.application.monitoring.monitor.PipelineMetricsReporter;
import com.study.webflux.rag.domain.monitoring.model.DialoguePipelineStage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Micrometer를 사용하여 파이프라인 메트릭을 Prometheus로 내보내는 Reporter입니다.
 */
@Component
public class MicrometerPipelineMetricsReporter implements PipelineMetricsReporter {

	private static final String METRIC_PREFIX = "dialogue.pipeline";

	private final MeterRegistry meterRegistry;

	public MicrometerPipelineMetricsReporter(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Override
	public void report(PipelineSummary summary) {
		recordPipelineMetrics(summary);
		recordStageMetrics(summary);
		recordStageGapMetrics(summary);
		recordLlmMetrics(summary);
		recordResponseLatencyMetrics(summary);
	}

	private void recordPipelineMetrics(PipelineSummary summary) {
		String status = summary.status().name().toLowerCase();

		// 파이프라인 실행 시간
		Timer.builder(METRIC_PREFIX + ".duration")
			.tag("status", status)
			.description("Total pipeline execution time")
			.register(meterRegistry)
			.record(summary.durationMillis(), TimeUnit.MILLISECONDS);

		// 파이프라인 실행 횟수
		Counter.builder(METRIC_PREFIX + ".executions")
			.tag("status", status)
			.description("Number of pipeline executions")
			.register(meterRegistry)
			.increment();

		// 입력 길이
		Object inputLength = summary.attributes().get("input.length");
		if (inputLength instanceof Number) {
			meterRegistry.summary(METRIC_PREFIX + ".input.length")
				.record(((Number) inputLength).doubleValue());
		}
	}

	private void recordStageMetrics(PipelineSummary summary) {
		for (StageSnapshot stage : summary.stages()) {
			String stageName = stage.stage().name().toLowerCase();
			String stageStatus = stage.status().name().toLowerCase();

			// 스테이지 실행 시간
			if (stage.durationMillis() >= 0) {
				Timer.builder(METRIC_PREFIX + ".stage.duration")
					.tag("stage", stageName)
					.tag("status", stageStatus)
					.description("Stage execution time")
					.register(meterRegistry)
					.record(stage.durationMillis(), TimeUnit.MILLISECONDS);
			}

			// 스테이지별 속성 메트릭
			recordStageAttributes(stage);
		}
	}

	private void recordStageAttributes(StageSnapshot stage) {
		String stageName = stage.stage().name().toLowerCase();

		// MEMORY_RETRIEVAL: 검색된 메모리 수
		if (stage.stage() == DialoguePipelineStage.MEMORY_RETRIEVAL) {
			recordCounterFromAttribute(stage,
				"memory.count",
				METRIC_PREFIX + ".memory.retrieved",
				stageName);

			// RAG 품질 메트릭 (Phase 1B)
			recordRagQualityMetrics(stage);
		}

		// RETRIEVAL: 검색된 문서 수
		if (stage.stage() == DialoguePipelineStage.RETRIEVAL) {
			recordCounterFromAttribute(stage,
				"document.count",
				METRIC_PREFIX + ".documents.retrieved",
				stageName);
		}

		// SENTENCE_ASSEMBLY: 생성된 문장 수
		if (stage.stage() == DialoguePipelineStage.SENTENCE_ASSEMBLY) {
			recordCounterFromAttribute(stage,
				"sentence.count",
				METRIC_PREFIX + ".sentences.generated",
				stageName);
		}

		// TTS_SYNTHESIS: 오디오 청크 수
		if (stage.stage() == DialoguePipelineStage.TTS_SYNTHESIS) {
			recordCounterFromAttribute(stage,
				"audio.chunks",
				METRIC_PREFIX + ".audio.chunks",
				stageName);
		}
	}

	/**
	 * RAG 품질 메트릭을 기록합니다. Phase 1B: RAG Quality Monitoring
	 */
	private void recordRagQualityMetrics(StageSnapshot stage) {
		// Memory count by type
		Object experientialCount = stage.attributes().get("memory.experiential.count");
		if (experientialCount instanceof Number) {
			meterRegistry.gauge("rag.memory.count",
				io.micrometer.core.instrument.Tags.of("memory_type", "experiential"),
				((Number) experientialCount).doubleValue());
		}

		Object factualCount = stage.attributes().get("memory.factual.count");
		if (factualCount instanceof Number) {
			meterRegistry.gauge("rag.memory.count",
				io.micrometer.core.instrument.Tags.of("memory_type", "factual"),
				((Number) factualCount).doubleValue());
		}
	}

	private void recordCounterFromAttribute(StageSnapshot stage,
		String attrKey,
		String metricName,
		String stageName) {
		Object value = stage.attributes().get(attrKey);
		if (value instanceof Number) {
			meterRegistry.summary(metricName)
				.record(((Number) value).doubleValue());
		}
	}

	private void recordLlmMetrics(PipelineSummary summary) {
		for (StageSnapshot stage : summary.stages()) {
			if (stage.stage() != DialoguePipelineStage.LLM_COMPLETION) {
				continue;
			}

			// 모델명
			Object model = stage.attributes().get("model");
			String modelTag = model != null ? model.toString() : "unknown";

			// 토큰 사용량
			recordTokenCounter(stage, "prompt.tokens", "prompt", modelTag);
			recordTokenCounter(stage, "completion.tokens", "completion", modelTag);
			recordTokenCounter(stage, "total.tokens", "total", modelTag);

			// 비용 (있는 경우)
			Object cost = stage.attributes().get("cost.usd");
			if (cost instanceof Number) {
				meterRegistry.gauge("llm.cost.usd", ((Number) cost).doubleValue());
			}
		}
	}

	private void recordTokenCounter(StageSnapshot stage,
		String attrKey,
		String tokenType,
		String model) {
		Object value = stage.attributes().get(attrKey);
		if (value instanceof Number) {
			Counter.builder("llm.tokens")
				.tag("type", tokenType)
				.tag("model", model)
				.description("LLM token usage")
				.register(meterRegistry)
				.increment(((Number) value).doubleValue());
		}
	}

	private void recordResponseLatencyMetrics(PipelineSummary summary) {
		// 첫 응답 지연 시간 (TTFB)
		if (summary.firstResponseLatencyMillis() != null) {
			Timer.builder(METRIC_PREFIX + ".response.first")
				.description("Time to first response")
				.register(meterRegistry)
				.record(summary.firstResponseLatencyMillis(), TimeUnit.MILLISECONDS);
		}

		// 마지막 응답 지연 시간 (TTLB)
		if (summary.lastResponseLatencyMillis() != null) {
			Timer.builder(METRIC_PREFIX + ".response.last")
				.description("Time to last response")
				.register(meterRegistry)
				.record(summary.lastResponseLatencyMillis(), TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Stage 간 Gap (전환 시간)을 기록합니다. Phase 1A: Pipeline Bottleneck Analysis
	 */
	private void recordStageGapMetrics(PipelineSummary summary) {
		var stages = summary.stages().stream()
			.sorted((a, b) -> {
				if (a.startedAt() == null)
					return 1;
				if (b.startedAt() == null)
					return -1;
				return a.startedAt().compareTo(b.startedAt());
			})
			.toList();

		for (int i = 0; i < stages.size() - 1; i++) {
			StageSnapshot current = stages.get(i);
			StageSnapshot next = stages.get(i + 1);

			// Gap 계산: 현재 Stage 종료 → 다음 Stage 시작
			if (current.finishedAt() != null && next.startedAt() != null) {
				long gapMillis = java.time.Duration.between(
					current.finishedAt(),
					next.startedAt()).toMillis();

				// Gap이 음수면 병렬 실행된 것이므로 0으로 처리
				if (gapMillis >= 0) {
					Timer.builder(METRIC_PREFIX + ".stage.gap")
						.tag("from_stage", current.stage().name().toLowerCase())
						.tag("to_stage", next.stage().name().toLowerCase())
						.description("Time gap between stage transitions")
						.register(meterRegistry)
						.record(gapMillis, TimeUnit.MILLISECONDS);
				}
			}
		}
	}
}
