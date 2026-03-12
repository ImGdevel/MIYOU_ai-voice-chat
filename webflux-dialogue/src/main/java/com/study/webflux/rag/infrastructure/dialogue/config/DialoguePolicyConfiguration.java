package com.study.webflux.rag.infrastructure.dialogue.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.study.webflux.rag.application.dialogue.policy.DialogueExecutionPolicy;
import com.study.webflux.rag.application.dialogue.policy.PromptTemplatePolicy;
import com.study.webflux.rag.application.dialogue.policy.SttPolicy;
import com.study.webflux.rag.application.memory.policy.MemoryExtractionPolicy;
import com.study.webflux.rag.application.memory.policy.MemoryRetrievalPolicy;
import com.study.webflux.rag.infrastructure.common.template.FileBasedPromptTemplate;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;

/** 프로퍼티 기반 애플리케이션 정책 빈을 생성합니다. */
@Configuration
public class DialoguePolicyConfiguration {

	@Bean
	public DialogueExecutionPolicy dialogueExecutionPolicy(RagDialogueProperties properties) {
		return new DialogueExecutionPolicy(properties.getOpenai().getModel());
	}

	@Bean
	public PromptTemplatePolicy promptTemplatePolicy(RagDialogueProperties properties,
		FileBasedPromptTemplate templateLoader) {
		return new PromptTemplatePolicy(
			resolveTemplate(templateLoader, properties.getSystemBasePromptTemplate()),
			resolveTemplate(templateLoader, properties.getSystemPromptTemplate()),
			resolveTemplate(templateLoader, properties.getCommonSystemPromptTemplate()),
			properties.getSystemPrompt());
	}

	@Bean
	public MemoryRetrievalPolicy memoryRetrievalPolicy(RagDialogueProperties properties) {
		var memory = properties.getMemory();
		return new MemoryRetrievalPolicy(memory.getImportanceBoost(),
			memory.getImportanceThreshold());
	}

	@Bean
	public MemoryExtractionPolicy memoryExtractionPolicy(RagDialogueProperties properties) {
		return new MemoryExtractionPolicy(properties.getMemory().getConversationThreshold());
	}

	@Bean
	public SttPolicy sttPolicy(RagDialogueProperties properties) {
		var stt = properties.getStt();
		return new SttPolicy(stt.getMaxFileSizeBytes(), stt.getLanguage());
	}

	private String resolveTemplate(FileBasedPromptTemplate loader, String templateName) {
		if (templateName == null || templateName.isBlank()) {
			return "";
		}
		try {
			return loader.load(templateName).trim();
		} catch (RuntimeException e) {
			return "";
		}
	}
}
