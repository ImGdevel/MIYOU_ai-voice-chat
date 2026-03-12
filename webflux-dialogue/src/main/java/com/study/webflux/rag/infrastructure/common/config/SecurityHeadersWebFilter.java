package com.study.webflux.rag.infrastructure.common.config;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * 로컬 실행과 프록시 뒤 응답 모두에 공통 보안 헤더를 추가합니다.
 */
@Component
public class SecurityHeadersWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		HttpHeaders headers = exchange.getResponse().getHeaders();
		putIfMissing(headers, "X-Content-Type-Options", "nosniff");
		putIfMissing(headers, "X-Frame-Options", "DENY");
		putIfMissing(headers, "Referrer-Policy", "strict-origin-when-cross-origin");
		putIfMissing(headers,
			"Permissions-Policy",
			"camera=(), microphone=(), geolocation=()");
		return chain.filter(exchange);
	}

	private void putIfMissing(HttpHeaders headers, String name, String value) {
		if (!headers.containsKey(name)) {
			headers.set(name, value);
		}
	}
}
