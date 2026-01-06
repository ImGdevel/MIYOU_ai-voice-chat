package com.study.webflux.rag.application.dialogue.pipeline.stage;

import java.util.List;
import java.util.StringJoiner;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.infrastructure.common.template.FileBasedPromptTemplate;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;

@Slf4j
@Service
public class SystemPromptService {

	private static final String PERSONA_PLACEHOLDER = "{{persona}}";
	private static final String COMMON_PLACEHOLDER = "{{common}}";
	private static final String MEMORIES_PLACEHOLDER = "{{memories}}";
	private static final String CONTEXT_PLACEHOLDER = "{{context}}";

	private static final String MEMORIES_TITLE = "대화 상대에 대한 기억:";
	private static final String EXPERIENTIAL_MEMORY_TITLE = "경험적 기억:";
	private static final String FACTUAL_MEMORY_TITLE = "사실 기반 기억:";
	private static final String CONTEXT_TITLE = "참고 정보:";

	private final FileBasedPromptTemplate promptTemplate;
	private final String configuredSystemPrompt;
	private final String baseTemplate;
	private final String personaTemplate;
	private final String commonTemplate;

	public SystemPromptService(FileBasedPromptTemplate promptTemplate,
		RagDialogueProperties properties) {
		this.promptTemplate = promptTemplate;
		this.configuredSystemPrompt = properties.getSystemPrompt();
		this.baseTemplate = resolveTemplateContent(properties.getSystemBasePromptTemplate());
		this.personaTemplate = resolveTemplateContent(properties.getSystemPromptTemplate());
		this.commonTemplate = resolveTemplateContent(properties.getCommonSystemPromptTemplate());
	}

	/**
	 * 템플릿/설정값과 회수된 메모리·검색 컨텍스트를 반영해 시스템 프롬프트를 생성합니다.
	 *
	 * @param context
	 *            현재 질의에 대한 검색 컨텍스트
	 * @param memories
	 *            현재 대화 상대에 대한 회수 메모리
	 * @return LLM 요청에 주입할 시스템 프롬프트
	 */
	public String buildSystemPrompt(RetrievalContext context, MemoryRetrievalResult memories) {
		PromptSections sections = new PromptSections(resolvePersonaPrompt(),
			commonTemplate,
			buildMemoryBlock(memories),
			buildContextBlock(context));

		if (!baseTemplate.isBlank()) {
			return applyTemplate(baseTemplate, sections);
		}

		return joinNonBlankBlocks(sections.persona(),
			sections.common(),
			sections.memories(),
			sections.context());
	}

	/**
	 * 페르소나 프롬프트를 템플릿 우선 정책으로 선택합니다.
	 */
	private String resolvePersonaPrompt() {
		if (!personaTemplate.isBlank()) {
			return personaTemplate;
		}
		if (configuredSystemPrompt != null) {
			return configuredSystemPrompt.trim();
		}
		return "";
	}

	/**
	 * 메모리 조회 결과를 시스템 프롬프트 블록 문자열로 직렬화합니다.
	 */
	private String buildMemoryBlock(MemoryRetrievalResult memories) {
		if (memories == null || memories.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder(MEMORIES_TITLE).append("\n");
		appendMemorySection(builder, EXPERIENTIAL_MEMORY_TITLE, memories.experientialMemories());
		appendMemorySection(builder, FACTUAL_MEMORY_TITLE, memories.factualMemories());

		return builder.toString().trim();
	}

	/**
	 * 검색 결과 문서 내용을 시스템 프롬프트 블록 문자열로 직렬화합니다.
	 */
	private String buildContextBlock(RetrievalContext context) {
		if (context == null || context.isEmpty()) {
			return "";
		}
		StringJoiner joiner = new StringJoiner("\n");
		context.documents().stream()
			.map(doc -> doc.content())
			.filter(content -> content != null && !content.isBlank())
			.forEach(joiner::add);
		if (joiner.length() == 0) {
			return "";
		}
		return CONTEXT_TITLE + "\n" + joiner;
	}

	/**
	 * 베이스 템플릿에 각 블록을 치환하고 공백 라인을 정규화합니다.
	 */
	private String applyTemplate(String template,
		PromptSections sections) {
		String rendered = template.replace(PERSONA_PLACEHOLDER, normalizeBlock(sections.persona()))
			.replace(COMMON_PLACEHOLDER, normalizeBlock(sections.common()))
			.replace(MEMORIES_PLACEHOLDER, normalizeBlock(sections.memories()))
			.replace(CONTEXT_PLACEHOLDER, normalizeBlock(sections.context()));
		return normalizeTemplateSpacing(rendered);
	}

	/**
	 * 템플릿이 없을 때 비어 있지 않은 블록만 순서대로 이어붙입니다.
	 */
	private String joinNonBlankBlocks(String... blocks) {
		StringJoiner joiner = new StringJoiner("\n\n");
		for (String block : blocks) {
			if (block != null && !block.isBlank()) {
				joiner.add(block.trim());
			}
		}
		return joiner.toString();
	}

	/**
	 * 설정된 템플릿 이름으로 파일 템플릿을 로드합니다. 실패 시 빈 문자열을 반환합니다.
	 */
	private String resolveTemplateContent(String templateName) {
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

	private void appendMemorySection(StringBuilder builder,
		String sectionTitle,
		List<Memory> memories) {
		if (memories == null || memories.isEmpty()) {
			return;
		}
		List<String> lines = memories.stream()
			.map(Memory::content)
			.filter(content -> content != null && !content.isBlank())
			.map(content -> "- " + content)
			.toList();
		if (lines.isEmpty()) {
			return;
		}
		builder.append("\n").append(sectionTitle).append("\n");
		lines.forEach(line -> builder.append(line).append("\n"));
	}

	private String normalizeBlock(String block) {
		return block == null ? "" : block.trim();
	}

	private String normalizeTemplateSpacing(String template) {
		String compacted = template.replaceAll("(?m)^[ \\t]+$", "");
		compacted = compacted.replaceAll("\\n{3,}", "\n\n");
		return compacted.trim();
	}

	private record PromptSections(
		String persona,
		String common,
		String memories,
		String context) {
	}
}
