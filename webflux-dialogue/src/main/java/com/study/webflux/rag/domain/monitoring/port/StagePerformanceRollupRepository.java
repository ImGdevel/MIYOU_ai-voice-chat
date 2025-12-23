package com.study.webflux.rag.domain.monitoring.port;

import java.time.Instant;

import com.study.webflux.rag.domain.monitoring.model.MetricsGranularity;
import com.study.webflux.rag.domain.monitoring.model.StagePerformanceRollup;
import reactor.core.publisher.Flux;

public interface StagePerformanceRollupRepository {
	Flux<StagePerformanceRollup> saveAll(Flux<StagePerformanceRollup> rollups);

	Flux<StagePerformanceRollup> findByGranularityAndBucketStartBetween(
		MetricsGranularity granularity,
		Instant startTime,
		Instant endTime);
}
