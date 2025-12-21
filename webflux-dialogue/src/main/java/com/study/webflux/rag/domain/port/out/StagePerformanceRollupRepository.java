package com.study.webflux.rag.domain.port.out;

import java.time.Instant;

import com.study.webflux.rag.domain.model.metrics.MetricsGranularity;
import com.study.webflux.rag.domain.model.metrics.StagePerformanceRollup;
import reactor.core.publisher.Flux;

public interface StagePerformanceRollupRepository {
	Flux<StagePerformanceRollup> saveAll(Flux<StagePerformanceRollup> rollups);

	Flux<StagePerformanceRollup> findByGranularityAndBucketStartBetween(
		MetricsGranularity granularity,
		Instant startTime,
		Instant endTime);
}
