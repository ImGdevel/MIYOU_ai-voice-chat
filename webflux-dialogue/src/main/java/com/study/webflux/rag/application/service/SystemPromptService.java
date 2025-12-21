package com.study.webflux.rag.application.service;

import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.model.memory.MemoryRetrievalResult;
import com.study.webflux.rag.domain.model.rag.RetrievalContext;
import com.study.webflux.rag.infrastructure.config.properties.RagDialogueProperties;
import com.study.webflux.rag.infrastructure.template.FileBasedPromptTemplate;

@Slf4j
@Service
public class SystemPromptService {

	private final FileBasedPromptTemplate promptTemplate;
	private final String configuredSystemPrompt;
	private final String configuredSystemPromptTemplate;
	private final String configuredCommonSystemPromptTemplate;
	private final String cachedSystemPromptFromTemplate;
	private final String cachedCommonSystemPromptFromTemplate;

	public SystemPromptService(FileBasedPromptTemplate promptTemplate,
		RagDialogueProperties properties) {
		this.promptTemplate = promptTemplate;
		this.configuredSystemPrompt = properties.getSystemPrompt();
		this.configuredSystemPromptTemplate = properties.getSystemPromptTemplate();
		this.configuredCommonSystemPromptTemplate = properties.getCommonSystemPromptTemplate();
		this.cachedSystemPromptFromTemplate = loadTemplate(configuredSystemPromptTemplate);
		this.cachedCommonSystemPromptFromTemplate = loadTemplate(
			configuredCommonSystemPromptTemplate);
	}

	public String buildSystemPrompt(RetrievalContext context, MemoryRetrievalResult memories) {
		StringBuilder prompt = new StringBuilder();

		String personaPrompt = choosePersonaPrompt();
		if (!personaPrompt.isBlank()) {
			prompt.append(personaPrompt).append("\n\n");
		}

		String commonPrompt = chooseCommonPrompt();
		if (!commonPrompt.isBlank()) {
			prompt.append(commonPrompt).append("\n\n");
		}

		if (!memories.isEmpty()) {
			prompt.append("대화 상대에 대한 기억:\n");

			if (!memories.experientialMemories().isEmpty()) {
				prompt.append("\n경험적 기억:\n");
				memories.experientialMemories()
					.forEach(m -> prompt.append("- ").append(m.content()).append("\n"));
			}

			if (!memories.factualMemories().isEmpty()) {
				prompt.append("\n사실 기반 기억:\n");
				memories.factualMemories()
					.forEach(m -> prompt.append("- ").append(m.content()).append("\n"));
			}

			prompt.append("\n");
		}

		if (!context.isEmpty()) {
			String contextText = context.documents().stream().map(doc -> doc.content())
				.collect(Collectors.joining("\n"));
			prompt.append("참고 정보:\n").append(contextText).append("\n\n");
		}

		return prompt.toString().trim();
	}

	private String choosePersonaPrompt() {
		if (!cachedSystemPromptFromTemplate.isBlank()) {
			return cachedSystemPromptFromTemplate;
		}
		if (configuredSystemPrompt != null) {
			return configuredSystemPrompt.trim();
		}
		return "";
	}

	private String chooseCommonPrompt() {
		return cachedCommonSystemPromptFromTemplate;
	}

	private String loadTemplate(String templateName) {
		if (templateName == null || templateName.isBlank()) {
			return "";
		}
		try {
			return promptTemplate.load(templateName).trim();
		} catch (RuntimeException e) {
			log.warn("시스템 프롬프트 템플릿 '{}'을 불러오지 못했습니다: {}",
				templateName,
				e.getMessage());
			return "";
		}
	}
}
