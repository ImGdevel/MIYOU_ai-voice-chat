package com.study.webflux.rag.infrastructure.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfiguration {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("RAG Dialogue API")
				.description("Real-time streaming RAG dialogue system with LLM and TTS integration")
				.version("1.0.0"));
	}
}
