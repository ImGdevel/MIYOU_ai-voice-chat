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
	private final String configuredBasePromptTemplate;
	private final String cachedBaseTemplate;
	private final String cachedSystemPromptFromTemplate;
	private final String cachedCommonSystemPromptFromTemplate;

	public SystemPromptService(FileBasedPromptTemplate promptTemplate,
		RagDialogueProperties properties) {
		this.promptTemplate = promptTemplate;
		this.configuredSystemPrompt = properties.getSystemPrompt();
		this.configuredSystemPromptTemplate = properties.getSystemPromptTemplate();
		this.configuredCommonSystemPromptTemplate = properties.getCommonSystemPromptTemplate();
		this.configuredBasePromptTemplate = properties.getSystemBasePromptTemplate();
		this.cachedBaseTemplate = loadTemplate(configuredBasePromptTemplate);
		this.cachedSystemPromptFromTemplate = loadTemplate(configuredSystemPromptTemplate);
		this.cachedCommonSystemPromptFromTemplate = loadTemplate(
			configuredCommonSystemPromptTemplate);
	}

	public String buildSystemPrompt(RetrievalContext context, MemoryRetrievalResult memories) {
		String personaPrompt = choosePersonaPrompt();
		String commonPrompt = chooseCommonPrompt();
		String memoryBlock = buildMemoryBlock(memories);
		String contextBlock = buildContextBlock(context);

		if (!cachedBaseTemplate.isBlank()) {
			return applyTemplate(cachedBaseTemplate,
				personaPrompt,
				commonPrompt,
				memoryBlock,
				contextBlock);
		}

		// Fallback: concatenate blocks if base template is missing
		StringBuilder fallback = new StringBuilder();
		if (!personaPrompt.isBlank()) {
			fallback.append(personaPrompt).append("\n\n");
		}
		if (!commonPrompt.isBlank()) {
			fallback.append(commonPrompt).append("\n\n");
		}
		if (!memoryBlock.isBlank()) {
			fallback.append(memoryBlock).append("\n\n");
		}
		if (!contextBlock.isBlank()) {
			fallback.append(contextBlock).append("\n\n");
		}
		return fallback.toString().trim();
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

	private String buildMemoryBlock(MemoryRetrievalResult memories) {
		if (memories == null || memories.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder("대화 상대에 대한 기억:\n");

		if (!memories.experientialMemories().isEmpty()) {
			builder.append("\n경험적 기억:\n");
			memories.experientialMemories()
				.forEach(m -> builder.append("- ").append(m.content()).append("\n"));
		}

		if (!memories.factualMemories().isEmpty()) {
			builder.append("\n사실 기반 기억:\n");
			memories.factualMemories()
				.forEach(m -> builder.append("- ").append(m.content()).append("\n"));
		}

		return builder.toString().trim();
	}

	private String buildContextBlock(RetrievalContext context) {
		if (context == null || context.isEmpty()) {
			return "";
		}
		String contextText = context.documents().stream().map(doc -> doc.content())
			.collect(Collectors.joining("\n"));
		return "참고 정보:\n" + contextText;
	}

	private String applyTemplate(String template,
		String personaPrompt,
		String commonPrompt,
		String memoryBlock,
		String contextBlock) {
		String result = template.replace("{{persona}}", personaPrompt)
			.replace("{{common}}", commonPrompt)
			.replace("{{memories}}", memoryBlock)
			.replace("{{context}}", contextBlock);

		// Collapse excessive blank lines
		result = result.replaceAll("(?m)^[ \\t]+$", ""); // strip whitespace-only lines
		result = result.replaceAll("\\n{3,}", "\n\n");
		return result.trim();
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
