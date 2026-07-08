package com.miyou.app.infrastructure.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class WebFluxCorsConfiguration(
    @Value("\${web.cors.allowed-origins}") allowedOrigins: List<String>,
    @Value("\${app.api.prefix:}") apiPrefix: String,
) : WebFluxConfigurer {
    private val allowedOrigins: List<String> =
        allowedOrigins
            .map { it.trim() }
            .filter { it.isNotBlank() }

    // apiPrefix를 정규화하여 전체 비즈니스 API 경로에 CORS 매핑을 한번에 적용.
    private val apiMapping: String =
        when {
            apiPrefix.isBlank() -> "/**"
            apiPrefix.startsWith("/") -> "${apiPrefix.trimEnd('/')}/**"
            else -> "/${apiPrefix.trimEnd('/')}/**"
        }

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

        registry
            .addMapping("/missions/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600)

        registry
            .addMapping("/auth/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600)
    }
}
