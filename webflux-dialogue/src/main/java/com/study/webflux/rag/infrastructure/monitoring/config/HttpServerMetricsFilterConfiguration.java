package com.study.webflux.rag.infrastructure.monitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.config.MeterFilter;

/**
 * 운영 트래픽 집계에서 actuator 요청 메트릭을 제외합니다.
 */
@Configuration
public class HttpServerMetricsFilterConfiguration {

	@Bean
	public MeterFilter actuatorRequestMetricsDenyFilter() {
		return MeterFilter.deny(id -> {
			if (!"http.server.requests".equals(id.getName())) {
				return false;
			}

			String uri = id.getTag("uri");
			return uri != null && uri.startsWith("/actuator");
		});
	}
}
