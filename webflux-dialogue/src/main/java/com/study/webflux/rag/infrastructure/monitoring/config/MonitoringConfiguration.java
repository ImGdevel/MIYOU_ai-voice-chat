package com.study.webflux.rag.infrastructure.monitoring.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.study.webflux.rag.application.monitoring.monitor.LoggingPipelineMetricsReporter;
import com.study.webflux.rag.application.monitoring.monitor.PersistentPipelineMetricsReporter;
import com.study.webflux.rag.application.monitoring.monitor.PipelineMetricsReporter;
import com.study.webflux.rag.domain.monitoring.port.PerformanceMetricsRepository;
import com.study.webflux.rag.domain.monitoring.port.UsageAnalyticsRepository;

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
