package com.study.webflux.rag.infrastructure.adapter.persistence.mongodb.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.study.webflux.rag.domain.model.metrics.MetricsGranularity;
import com.study.webflux.rag.domain.model.metrics.MetricsRollup;

@Document(collection = "metrics_rollups")
public record MetricsRollupEntity(
	@Id String id,
	@Indexed Instant bucketStart,
	@Indexed String granularity,
	long requestCount,
	long totalTokens,
	long totalDurationMillis,
	double avgResponseMillis
) {
	public static MetricsRollupEntity fromDomain(MetricsRollup domain) {
		String id = domain.granularity().name() + "-" + domain.bucketStart().toEpochMilli();
		return new MetricsRollupEntity(
			id,
			domain.bucketStart(),
			domain.granularity().name(),
			domain.requestCount(),
			domain.totalTokens(),
			domain.totalDurationMillis(),
			domain.avgResponseMillis());
	}

	public MetricsRollup toDomain() {
		return new MetricsRollup(
			bucketStart,
			MetricsGranularity.valueOf(granularity),
			requestCount,
			totalTokens,
			totalDurationMillis,
			avgResponseMillis);
	}
}
