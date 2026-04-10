package com.miyou.app.infrastructure.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class WebFluxCorsConfiguration(
    @Value("\${web.cors.allowed-origins}") allowedOrigins: List<String>,
) : WebFluxConfigurer {
    private val allowedOrigins: List<String> =
        allowedOrigins
            .map { it.trim() }
            .filter { it.isNotBlank() }

    override fun addCorsMappings(registry: CorsRegistry) {
        if (allowedOrigins.isEmpty()) {
            return
        }

        registry
            .addMapping("/rag/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600)
    }
}
