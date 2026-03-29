package com.miyou.app.domain.monitoring.port;

import java.time.Instant;

import com.miyou.app.domain.monitoring.model.MetricsGranularity;
import com.miyou.app.domain.monitoring.model.StagePerformanceRollup;
import reactor.core.publisher.Flux;

public interface StagePerformanceRollupRepository {
	Flux<StagePerformanceRollup> saveAll(Flux<StagePerformanceRollup> rollups);

	Flux<StagePerformanceRollup> findByGranularityAndBucketStartBetween(
		MetricsGranularity granularity,
		Instant startTime,
		Instant endTime);
}
