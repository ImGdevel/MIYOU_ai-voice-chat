package com.study.webflux.rag.application.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class DashboardController {

	@GetMapping("/dashboard")
	public Mono<Void> dashboard(ServerHttpResponse response) {
		response.setStatusCode(HttpStatus.TEMPORARY_REDIRECT);
		response.getHeaders().setLocation(URI.create("/dashboard.html"));
		return response.setComplete();
	}

	@GetMapping("/monitoring")
	public Mono<Void> monitoring(ServerHttpResponse response) {
		response.setStatusCode(HttpStatus.TEMPORARY_REDIRECT);
		response.getHeaders().setLocation(URI.create("/dashboard.html"));
		return response.setComplete();
	}
}
