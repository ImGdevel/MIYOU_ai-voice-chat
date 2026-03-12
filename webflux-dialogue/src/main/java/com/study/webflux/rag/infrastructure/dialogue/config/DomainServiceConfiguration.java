package com.study.webflux.rag.infrastructure.dialogue.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.study.webflux.rag.domain.dialogue.service.SentenceAssembler;

/** 프레임워크 비의존 도메인 서비스 빈을 등록합니다. */
@Configuration
public class DomainServiceConfiguration {

	@Bean
	public SentenceAssembler sentenceAssembler() {
		return new SentenceAssembler();
	}
}
