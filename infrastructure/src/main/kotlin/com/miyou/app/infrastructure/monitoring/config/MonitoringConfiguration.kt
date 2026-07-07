package com.miyou.app.infrastructure.monitoring.config

import com.miyou.app.domain.monitoring.port.PerformanceMetricsRepository
import com.miyou.app.domain.monitoring.port.PipelineMetricsReporter
import com.miyou.app.domain.monitoring.port.UsageAnalyticsRepository
import com.miyou.app.infrastructure.monitoring.micrometer.CompositePipelineMetricsReporter
import com.miyou.app.infrastructure.monitoring.micrometer.MicrometerPipelineMetricsReporter
import com.miyou.app.infrastructure.outbound.monitoring.LoggingPipelineMetricsReporter
import com.miyou.app.infrastructure.outbound.monitoring.PersistentPipelineMetricsReporter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * 모니터링 파이프라인 보고자 구성을 정의합니다.
 */
@Configuration
class MonitoringConfiguration {
    @Bean
    @Primary
    @ConditionalOnProperty(
        name = ["monitoring.persistent.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun compositePipelineMetricsReporter(
        performanceMetricsRepository: PerformanceMetricsRepository,
        usageAnalyticsRepository: UsageAnalyticsRepository,
        loggingReporter: LoggingPipelineMetricsReporter,
        micrometerReporter: MicrometerPipelineMetricsReporter,
    ): PipelineMetricsReporter {
        val persistentReporter =
            PersistentPipelineMetricsReporter(
                performanceMetricsRepository,
                usageAnalyticsRepository,
                loggingReporter,
            )
        return CompositePipelineMetricsReporter(
            listOf(
                persistentReporter,
                micrometerReporter,
            ),
        )
    }

    @Bean
    @ConditionalOnProperty(name = ["monitoring.persistent.enabled"], havingValue = "false")
    fun micrometerOnlyReporter(
        micrometerReporter: MicrometerPipelineMetricsReporter,
        loggingReporter: LoggingPipelineMetricsReporter,
    ): PipelineMetricsReporter =
        CompositePipelineMetricsReporter(
            listOf(
                micrometerReporter,
                loggingReporter,
            ),
        )
}
