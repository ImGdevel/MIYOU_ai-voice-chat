package com.study.webflux.rag.infrastructure.adapter.persistence.mongodb.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.study.webflux.rag.domain.model.metrics.PerformanceMetrics;

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
				domain.attributes());
		}

		public PerformanceMetrics.StagePerformance toDomain() {
			return new PerformanceMetrics.StagePerformance(
				stageName,
				status,
				startedAt,
				finishedAt,
				durationMillis,
				attributes);
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
			domain.stages().stream().map(StagePerformanceEntity::fromDomain).toList(),
			domain.systemAttributes());
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
			stages.stream().map(StagePerformanceEntity::toDomain).toList(),
			systemAttributes);
	}
}
