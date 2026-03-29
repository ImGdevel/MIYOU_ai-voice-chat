package com.miyou.app.infrastructure.monitoring.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.miyou.app.domain.monitoring.model.MetricsGranularity;
import com.miyou.app.domain.monitoring.model.MetricsRollup;

@Document(collection = "metrics_rollups")
public record MetricsRollupDocument(
	@Id String id,
	@Indexed Instant bucketStart,
	@Indexed String granularity,
	long requestCount,
	long totalTokens,
	long totalDurationMillis,
	double avgResponseMillis
) {
	public static MetricsRollupDocument fromDomain(MetricsRollup domain) {
		String id = domain.granularity().name() + "-" + domain.bucketStart().toEpochMilli();
		return new MetricsRollupDocument(
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
