package com.study.webflux.rag.domain.monitoring.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.study.webflux.rag.domain.monitoring.model.PerformanceMetrics;

@Document(collection = "performance_metrics")
public record PerformanceMetricsEntity(
	@Id String pipelineId,
	String status,
	@Indexed Instant startedAt,
	Instant finishedAt,
	@Indexed long totalDurationMillis,
	Long firstResponseLatencyMillis,
	Long lastResponseLatencyMillis,
	List<StagePerformanceEntity> stages,
	Map<String, Object> systemAttributes
) {
	public record StagePerformanceEntity(
		String stageName,
		String status,
		Instant startedAt,
		Instant finishedAt,
		long durationMillis,
		Map<String, Object> attributes) {
		public static StagePerformanceEntity fromDomain(
			PerformanceMetrics.StagePerformance domain) {
			return new StagePerformanceEntity(
				domain.stageName(),
				domain.status(),
				domain.startedAt(),
				domain.finishedAt(),
				domain.durationMillis(),
				sanitizeMapKeys(domain.attributes()));
		}

		public PerformanceMetrics.StagePerformance toDomain() {
			return new PerformanceMetrics.StagePerformance(
				stageName,
				status,
				startedAt,
				finishedAt,
				durationMillis,
				restoreMapKeys(attributes));
		}
	}

	public static PerformanceMetricsEntity fromDomain(PerformanceMetrics domain) {
		return new PerformanceMetricsEntity(
			domain.pipelineId(),
			domain.status(),
			domain.startedAt(),
			domain.finishedAt(),
			domain.totalDurationMillis(),
			domain.firstResponseLatencyMillis(),
			domain.lastResponseLatencyMillis(),
			domain.stages() == null
				? java.util.List.of()
				: domain.stages().stream().map(StagePerformanceEntity::fromDomain).toList(),
			sanitizeMapKeys(domain.systemAttributes()));
	}

	public PerformanceMetrics toDomain() {
		return new PerformanceMetrics(
			pipelineId,
			status,
			startedAt,
			finishedAt,
			totalDurationMillis,
			firstResponseLatencyMillis,
			lastResponseLatencyMillis,
			stages == null
				? java.util.List.of()
				: stages.stream().map(StagePerformanceEntity::toDomain).toList(),
			restoreMapKeys(systemAttributes));
	}

	private static Map<String, Object> sanitizeMapKeys(Map<String, Object> map) {
		if (map == null) {
			return null;
		}
		return map.entrySet().stream()
			.collect(Collectors.toMap(
				e -> e.getKey().replace(".", "__DOT__"),
				Map.Entry::getValue));
	}

	private static Map<String, Object> restoreMapKeys(Map<String, Object> map) {
		if (map == null) {
			return null;
		}
		return map.entrySet().stream()
			.collect(Collectors.toMap(
				e -> e.getKey().replace("__DOT__", "."),
				Map.Entry::getValue));
	}
}
