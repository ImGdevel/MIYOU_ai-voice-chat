package com.miyou.app.domain.monitoring.port;

import java.time.Instant;

import com.miyou.app.domain.monitoring.model.MetricsGranularity;
import com.miyou.app.domain.monitoring.model.MetricsRollup;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MetricsRollupRepository {
	Mono<MetricsRollup> save(MetricsRollup rollup);

	Flux<MetricsRollup> findByGranularityAndBucketStartBetweenOrderByBucketStartAsc(
		MetricsGranularity granularity,
		Instant startTime,
		Instant endTime);
}
