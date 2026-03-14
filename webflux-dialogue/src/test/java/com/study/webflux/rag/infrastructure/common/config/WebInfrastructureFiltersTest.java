package com.study.webflux.rag.infrastructure.common.config;

import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebInfrastructureFiltersTest {

	@Test
	@DisplayName("일반 응답에도 기본 보안 헤더를 포함한다")
	void request_shouldIncludeSecurityHeaders() {
		SecurityHeadersWebFilter securityHeadersWebFilter = new SecurityHeadersWebFilter();
		WebFilterChain okChain = exchange -> {
			exchange.getResponse().setStatusCode(HttpStatus.OK);
			return exchange.getResponse().setComplete();
		};
		MockServerWebExchange exchange = MockServerWebExchange.from(
			MockServerHttpRequest.post("/rag/dialogue/text").build());

		securityHeadersWebFilter.filter(exchange, okChain).block();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
			.isEqualTo("nosniff");
		assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options"))
			.isEqualTo("DENY");
		assertThat(exchange.getResponse().getHeaders().getFirst("Referrer-Policy"))
			.isEqualTo("strict-origin-when-cross-origin");
		assertThat(exchange.getResponse().getHeaders().getFirst("Permissions-Policy"))
			.isEqualTo("camera=(), microphone=(), geolocation=()");
	}
}
