package com.study.webflux.rag.application.dialogue.pipeline.stage;

import java.util.List;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.memory.model.MemoryType;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.model.RetrievalDocument;
import com.study.webflux.rag.infrastructure.common.template.FileBasedPromptTemplate;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemPromptServiceTest {

	@Mock
	private FileBasedPromptTemplate promptTemplate;

	private RagDialogueProperties properties;

	@BeforeEach
	void setUp() {
		properties = new RagDialogueProperties();
	}

	@Test
	void buildSystemPrompt_shouldRenderBaseTemplateWithAllSections() {
		when(promptTemplate.load("system/base"))
			.thenReturn("{{persona}}\n\n{{common}}\n\n{{memories}}\n\n{{context}}");
		when(promptTemplate.load("system/persona/maid")).thenReturn("persona");
		when(promptTemplate.load("system/common")).thenReturn("common");

		SystemPromptService service = new SystemPromptService(promptTemplate, properties);

		UserId userId = UserId.of("user-1");
		Memory experiential = Memory.create(userId, MemoryType.EXPERIENTIAL, "사용자는 러닝을 좋아한다.",
			0.8f);
		Memory factual = Memory.create(userId, MemoryType.FACTUAL, "사용자의 직업은 개발자다.", 0.9f);
		MemoryRetrievalResult memories = MemoryRetrievalResult.of(List.of(experiential),
			List.of(factual));
		RetrievalContext context = RetrievalContext.of("사용자 취미",
			List.of(RetrievalDocument.of("러닝은 체력 향상에 도움이 된다.", 90)));

		String prompt = service.buildSystemPrompt(context, memories);

		assertThat(prompt).contains("persona").contains("common").contains("대화 상대에 대한 기억:")
			.contains("경험적 기억:").contains("사실 기반 기억:")
			.contains("- 사용자는 러닝을 좋아한다.").contains("- 사용자의 직업은 개발자다.")
			.contains("참고 정보:").contains("러닝은 체력 향상에 도움이 된다.");
	}

	@Test
	void buildSystemPrompt_shouldFallbackToConfiguredSystemPromptWhenPersonaTemplateIsMissing() {
		properties.setSystemPrompt("configured persona");

		when(promptTemplate.load("system/base")).thenThrow(new RuntimeException("base missing"));
		when(promptTemplate.load("system/persona/maid"))
			.thenThrow(new RuntimeException("persona missing"));
		when(promptTemplate.load("system/common")).thenReturn("common");

		SystemPromptService service = new SystemPromptService(promptTemplate, properties);

		String prompt = service.buildSystemPrompt(RetrievalContext.empty("query"),
			MemoryRetrievalResult.empty());

		assertThat(prompt).isEqualTo("configured persona\n\ncommon");
	}
}
