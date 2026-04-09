package com.miyou.app.infrastructure.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {
    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("RAG Dialogue API")
                    .description("Real-time streaming RAG dialogue system with LLM and TTS integration")
                    .version("1.0.0"),
            )
}
