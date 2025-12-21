package com.study.webflux.rag.domain.port.out;

import java.time.Instant;

import com.study.webflux.rag.domain.model.metrics.MetricsGranularity;
import com.study.webflux.rag.domain.model.metrics.MetricsRollup;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MetricsRollupRepository {
	Mono<MetricsRollup> save(MetricsRollup rollup);

	Flux<MetricsRollup> findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
		MetricsGranularity granularity,
		Instant startTime,
		Instant endTime);
}
