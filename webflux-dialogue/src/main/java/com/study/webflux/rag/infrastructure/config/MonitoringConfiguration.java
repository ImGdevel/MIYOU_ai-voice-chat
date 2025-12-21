package com.study.webflux.rag.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.study.webflux.rag.application.monitoring.LoggingPipelineMetricsReporter;
import com.study.webflux.rag.application.monitoring.PersistentPipelineMetricsReporter;
import com.study.webflux.rag.application.monitoring.PipelineMetricsReporter;
import com.study.webflux.rag.domain.port.out.PerformanceMetricsRepository;
import com.study.webflux.rag.domain.port.out.UsageAnalyticsRepository;

@Configuration
public class MonitoringConfiguration {

	@Bean
	@Primary
	@ConditionalOnProperty(name = "monitoring.persistent.enabled", havingValue = "true", matchIfMissing = true)
	public PipelineMetricsReporter persistentPipelineMetricsReporter(
		PerformanceMetricsRepository performanceMetricsRepository,
		UsageAnalyticsRepository usageAnalyticsRepository,
		LoggingPipelineMetricsReporter loggingReporter) {
		return new PersistentPipelineMetricsReporter(
			performanceMetricsRepository,
			usageAnalyticsRepository,
			loggingReporter);
	}
}
