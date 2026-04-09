package com.miyou.app.infrastructure.monitoring.config

import io.micrometer.core.instrument.config.MeterFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * actuator endpoint 접근을 제외한 actuator 요청 metrics 수집을 제외하기 위한 설정입니다.
 */
@Configuration
class HttpServerMetricsFilterConfiguration {
    @Bean
    fun actuatorRequestMetricsDenyFilter(): MeterFilter =
        MeterFilter.deny { id ->
            if (id.name != "http.server.requests") {
                false
            } else {
                id.getTag("uri")?.startsWith("/actuator") == true
            }
        }
}
