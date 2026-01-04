package com.study.webflux.rag.domain.monitoring.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.study.webflux.rag.domain.monitoring.model.MetricsGranularity;
import com.study.webflux.rag.domain.monitoring.model.StagePerformanceRollup;

@Document(collection = "stage_performance_rollups")
@CompoundIndex(name = "granularity_bucket_stage", def = "{'granularity': 1, 'bucketStart': 1, 'stageName': 1}")
public record StagePerformanceRollupEntity(
	@Id String id,
	@Indexed Instant bucketStart,
	@Indexed String granularity,
	String stageName,
	long count,
	long totalDurationMillis,
	double avgDurationMillis
) {
	public static StagePerformanceRollupEntity fromDomain(StagePerformanceRollup domain) {
		String id = domain.granularity().name() + "-" + domain.bucketStart().toEpochMilli()
			+ "-" + domain.stageName();
		return new StagePerformanceRollupEntity(
			id,
			domain.bucketStart(),
			domain.granularity().name(),
			domain.stageName(),
			domain.count(),
			domain.totalDurationMillis(),
			domain.avgDurationMillis());
	}

	public StagePerformanceRollup toDomain() {
		return new StagePerformanceRollup(
			bucketStart,
			MetricsGranularity.valueOf(granularity),
			stageName,
			count,
			totalDurationMillis,
			avgDurationMillis);
	}
}
