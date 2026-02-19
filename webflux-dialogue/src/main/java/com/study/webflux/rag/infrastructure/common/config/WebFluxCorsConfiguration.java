package com.study.webflux.rag.infrastructure.common.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebFluxCorsConfiguration implements WebFluxConfigurer {

	private final List<String> allowedOrigins;

	public WebFluxCorsConfiguration(
		@Value("${web.cors.allowed-origins}") List<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins.stream().map(String::trim)
			.filter(origin -> !origin.isBlank()).toList();
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (allowedOrigins.isEmpty()) {
			return;
		}

		registry.addMapping("/rag/**")
			.allowedOrigins(allowedOrigins.toArray(String[]::new))
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("*")
			.maxAge(3600);
	}
}
