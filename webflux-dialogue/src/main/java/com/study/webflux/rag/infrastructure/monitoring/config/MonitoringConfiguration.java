package com.study.webflux.rag.infrastructure.monitoring.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.study.webflux.rag.application.monitoring.monitor.LoggingPipelineMetricsReporter;
import com.study.webflux.rag.application.monitoring.monitor.PersistentPipelineMetricsReporter;
import com.study.webflux.rag.application.monitoring.monitor.PipelineMetricsReporter;
import com.study.webflux.rag.domain.monitoring.port.PerformanceMetricsRepository;
import com.study.webflux.rag.domain.monitoring.port.UsageAnalyticsRepository;
import com.study.webflux.rag.infrastructure.monitoring.micrometer.CompositePipelineMetricsReporter;
import com.study.webflux.rag.infrastructure.monitoring.micrometer.MicrometerPipelineMetricsReporter;

/** 파이프라인 모니터링 설정을 제공합니다. */
@Configuration
public class MonitoringConfiguration {

	/** 영구 저장 + Micrometer Reporter를 조합한 Composite Reporter를 생성합니다. */
	@Bean
	@Primary
	@ConditionalOnProperty(name = "monitoring.persistent.enabled", havingValue = "true", matchIfMissing = true)
	public PipelineMetricsReporter compositePipelineMetricsReporter(
		PerformanceMetricsRepository performanceMetricsRepository,
		UsageAnalyticsRepository usageAnalyticsRepository,
		LoggingPipelineMetricsReporter loggingReporter,
		MicrometerPipelineMetricsReporter micrometerReporter) {

		PersistentPipelineMetricsReporter persistentReporter = new PersistentPipelineMetricsReporter(
			performanceMetricsRepository,
			usageAnalyticsRepository,
			loggingReporter);

		return new CompositePipelineMetricsReporter(
			List.of(persistentReporter, micrometerReporter));
	}

	/** 영구 저장 비활성화 시 Micrometer Reporter만 사용합니다. */
	@Bean
	@ConditionalOnProperty(name = "monitoring.persistent.enabled", havingValue = "false")
	public PipelineMetricsReporter micrometerOnlyReporter(
		MicrometerPipelineMetricsReporter micrometerReporter,
		LoggingPipelineMetricsReporter loggingReporter) {
		return new CompositePipelineMetricsReporter(List.of(micrometerReporter, loggingReporter));
	}
}
