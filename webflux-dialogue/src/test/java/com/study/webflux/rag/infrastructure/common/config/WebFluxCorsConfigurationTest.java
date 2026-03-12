package com.study.webflux.rag.infrastructure.common.config;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebFluxCorsConfigurationTest {

	@Test
	@DisplayName("허용된 Origin과 메서드와 헤더를 CORS 설정에 반영한다")
	void buildCorsConfiguration_shouldReflectConfiguredOriginsMethodsAndHeaders() {
		WebFluxCorsConfiguration configuration = new WebFluxCorsConfiguration(
			List.of("https://frontend.example.com"),
			List.of("GET", "POST", "OPTIONS"),
			List.of("accept",
				"authorization",
				"cache-control",
				"content-type",
				"origin",
				"pragma",
				"x-requested-with"));

		CorsConfiguration corsConfiguration = configuration.buildCorsConfiguration();

		assertThat(corsConfiguration.checkOrigin("https://frontend.example.com"))
			.isEqualTo("https://frontend.example.com");
		assertThat(corsConfiguration.checkHttpMethod(HttpMethod.POST))
			.containsExactly(HttpMethod.GET, HttpMethod.POST, HttpMethod.OPTIONS);
		assertThat(corsConfiguration.checkHeaders(List.of("content-type")))
			.contains("content-type");
		assertThat(corsConfiguration.getExposedHeaders())
			.containsExactly(HttpHeaders.CONTENT_TYPE);
	}
}
