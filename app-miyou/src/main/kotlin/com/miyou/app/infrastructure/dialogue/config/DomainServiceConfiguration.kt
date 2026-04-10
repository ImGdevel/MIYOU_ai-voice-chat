package com.miyou.app.infrastructure.dialogue.config

import com.miyou.app.domain.dialogue.service.SentenceAssembler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DomainServiceConfiguration {
    @Bean
    fun sentenceAssembler(): SentenceAssembler = SentenceAssembler()
}
