package com.study.webflux.rag.infrastructure.common.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class WebFluxCorsConfiguration {

	private final List<String> allowedOrigins;
	private final List<String> allowedMethods;
	private final List<String> allowedHeaders;

	public WebFluxCorsConfiguration(
		@Value("${web.cors.allowed-origins}") List<String> allowedOrigins,
		@Value("${web.cors.allowed-methods:GET,POST,OPTIONS}") List<String> allowedMethods,
		@Value("${web.cors.allowed-headers:accept,authorization,cache-control,content-type,origin,pragma,x-requested-with}") List<String> allowedHeaders) {
		this.allowedOrigins = sanitize(allowedOrigins);
		this.allowedMethods = sanitize(allowedMethods);
		this.allowedHeaders = sanitizeHeaders(allowedHeaders);
	}

	@Bean
	public CorsWebFilter corsWebFilter() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		if (allowedOrigins.isEmpty()) {
			return new CorsWebFilter(source);
		}

		source.registerCorsConfiguration("/**", buildCorsConfiguration());
		return new CorsWebFilter(source);
	}

	CorsConfiguration buildCorsConfiguration() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(allowedMethods);
		configuration.setAllowedHeaders(allowedHeaders);
		configuration.setExposedHeaders(List.of(HttpHeaders.CONTENT_TYPE));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);
		return configuration;
	}

	private List<String> sanitize(List<String> values) {
		return values.stream()
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.toList();
	}

	private List<String> sanitizeHeaders(List<String> values) {
		return values.stream()
			.map(String::trim)
			.map(String::toLowerCase)
			.filter(value -> !value.isBlank())
			.toList();
	}
}
