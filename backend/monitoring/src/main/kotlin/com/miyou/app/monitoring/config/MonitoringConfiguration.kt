package com.miyou.app.monitoring.config

import com.miyou.app.monitoring.adapter.LoggingPipelineMetricsReporter
import com.miyou.app.monitoring.adapter.StructuredLogPipelineMetricsReporter
import com.miyou.app.monitoring.micrometer.CompositePipelineMetricsReporter
import com.miyou.app.monitoring.micrometer.MicrometerPipelineMetricsReporter
import com.miyou.app.monitoring.port.PipelineMetricsReporter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * 모니터링 파이프라인 보고자 구성을 정의합니다.
 *
 * 건당 상세(프롬프트/RAG/LLM 응답)는 [StructuredLogPipelineMetricsReporter]가 전용
 * 로그 파일로, 기간별 집계(비용/트래픽/병목)는 [MicrometerPipelineMetricsReporter]가
 * Prometheus로 각각 전담한다. Mongo 적재는 하지 않는다.
 */
@Configuration
class MonitoringConfiguration {
    @Bean
    @Primary
    fun pipelineMetricsReporter(
        loggingReporter: LoggingPipelineMetricsReporter,
        structuredLogReporter: StructuredLogPipelineMetricsReporter,
        micrometerReporter: MicrometerPipelineMetricsReporter,
    ): PipelineMetricsReporter =
        CompositePipelineMetricsReporter(
            listOf(
                loggingReporter,
                structuredLogReporter,
                micrometerReporter,
            ),
        )
}
