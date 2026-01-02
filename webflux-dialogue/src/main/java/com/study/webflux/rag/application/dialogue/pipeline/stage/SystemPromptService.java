package com.study.webflux.rag.application.dialogue.pipeline.stage;

import java.util.StringJoiner;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.infrastructure.common.template.FileBasedPromptTemplate;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;

@Slf4j
@Service
public class SystemPromptService {

	private final FileBasedPromptTemplate promptTemplate;
	private final String configuredSystemPrompt;
	private final String configuredSystemPromptTemplate;
	private final String configuredCommonSystemPromptTemplate;
	private final String configuredBasePromptTemplate;

	private final String cachedBaseTemplate;
	private final String cachedPersonaFromTemplate;
	private final String cachedCommonFromTemplate;

	public SystemPromptService(FileBasedPromptTemplate promptTemplate,
		RagDialogueProperties properties) {
		this.promptTemplate = promptTemplate;
		this.configuredSystemPrompt = properties.getSystemPrompt();
		this.configuredSystemPromptTemplate = properties.getSystemPromptTemplate();
		this.configuredCommonSystemPromptTemplate = properties.getCommonSystemPromptTemplate();
		this.configuredBasePromptTemplate = properties.getSystemBasePromptTemplate();
		this.cachedBaseTemplate = loadTemplate(configuredBasePromptTemplate);
		this.cachedPersonaFromTemplate = loadTemplate(configuredSystemPromptTemplate);
		this.cachedCommonFromTemplate = loadTemplate(configuredCommonSystemPromptTemplate);
	}

	/**
	 * 메모리·검색 컨텍스트를 반영한 시스템 프롬프트를 생성합니다.
	 */
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

		// 템플릿이 없으면 블록을 순서대로 이어붙인다.
		return joinNonBlankBlocks(personaPrompt, commonPrompt, memoryBlock, contextBlock);
	}

	private String choosePersonaPrompt() {
		if (!cachedPersonaFromTemplate.isBlank()) {
			return cachedPersonaFromTemplate;
		}
		if (configuredSystemPrompt != null) {
			return configuredSystemPrompt.trim();
		}
		return "";
	}

	private String chooseCommonPrompt() {
		return cachedCommonFromTemplate;
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
		StringJoiner joiner = new StringJoiner("\n");
		context.documents().forEach(doc -> joiner.add(doc.content()));
		return "참고 정보:\n" + joiner.toString();
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

	private String joinNonBlankBlocks(String... blocks) {
		StringJoiner joiner = new StringJoiner("\n\n");
		for (String block : blocks) {
			if (block != null && !block.isBlank()) {
				joiner.add(block.trim());
			}
		}
		return joiner.toString();
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
