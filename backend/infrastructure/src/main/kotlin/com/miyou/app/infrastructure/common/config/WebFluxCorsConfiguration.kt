package com.miyou.app.infrastructure.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class WebFluxCorsConfiguration(
    @Value("\${web.cors.allowed-origins}") allowedOrigins: List<String>,
    @Value("\${spring.webflux.base-path:}") basePath: String,
) : WebFluxConfigurer {
    private val allowedOrigins: List<String> =
        allowedOrigins
            .map { it.trim() }
            .filter { it.isNotBlank() }

    // 모든 비즈니스 API가 base-path(/api/v1) 아래에 있으므로 매핑 하나로 전체를 커버한다.
    // 컨트롤러 경로가 늘어날 때마다 CORS 설정을 따로 추가할 필요가 없다.
    private val apiMapping: String = "${basePath.trimEnd('/')}/**"

    override fun addCorsMappings(registry: CorsRegistry) {
        if (allowedOrigins.isEmpty()) {
            return
        }

        registry
            .addMapping(apiMapping)
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600)
    }
}
