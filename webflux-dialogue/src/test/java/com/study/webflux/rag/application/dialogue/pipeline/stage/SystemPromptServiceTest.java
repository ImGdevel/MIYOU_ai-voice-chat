package com.study.webflux.rag.application.dialogue.pipeline.stage;

import java.util.List;

import com.study.webflux.rag.application.dialogue.policy.PromptTemplatePolicy;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.port.TemplateLoaderPort;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.memory.model.MemoryType;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.model.RetrievalDocument;
import com.study.webflux.rag.fixture.ConversationSessionFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemPromptServiceTest {

	@Mock
	private TemplateLoaderPort templateLoader;

	@Test
	@DisplayName("시스템 프롬프트 생성 시 모든 섹션을 포함한다")
	void buildSystemPrompt_shouldRenderBaseTemplateWithAllSections() {
		when(templateLoader.load("system/persona/maid")).thenReturn("persona");

		PromptTemplatePolicy policy = new PromptTemplatePolicy(
			"{{persona}}\n\n{{common}}\n\n{{memories}}\n\n{{context}}",
			"",
			"common",
			null);

		SystemPromptService service = new SystemPromptService(templateLoader, policy);

		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		Memory experiential = Memory.create(sessionId, MemoryType.EXPERIENTIAL, "사용자는 러닝을 좋아한다.",
			0.8f);
		Memory factual = Memory.create(sessionId, MemoryType.FACTUAL, "사용자의 직업은 개발자다.", 0.9f);
		MemoryRetrievalResult memories = MemoryRetrievalResult.of(List.of(experiential),
			List.of(factual));
		RetrievalContext context = RetrievalContext.of("사용자 취미",
			List.of(RetrievalDocument.of("러닝은 체력 향상에 도움이 된다.", 90)));

		String prompt = service.buildSystemPrompt(PersonaId.of("maid"), context, memories);

		assertThat(prompt).contains("persona").contains("common").contains("대화 상대에 대한 기억:")
			.contains("경험적 기억:").contains("사실 기반 기억:")
			.contains("- 사용자는 러닝을 좋아한다.").contains("- 사용자의 직업은 개발자다.")
			.contains("참고 정보:").contains("러닝은 체력 향상에 도움이 된다.");
	}

	@Test
	@DisplayName("페르소나 템플릿 누락 시 기본 프롬프트로 폴백한다")
	void buildSystemPrompt_shouldFallbackToConfiguredSystemPromptWhenPersonaTemplateIsMissing() {
		when(templateLoader.load("system/persona/maid"))
			.thenThrow(new RuntimeException("persona missing"));

		PromptTemplatePolicy policy = new PromptTemplatePolicy(
			"",
			"",
			"common",
			"configured persona");

		SystemPromptService service = new SystemPromptService(templateLoader, policy);

		String prompt = service.buildSystemPrompt(PersonaId.of("maid"),
			RetrievalContext.empty("query"),
			MemoryRetrievalResult.empty());

		assertThat(prompt).isEqualTo("configured persona\n\ncommon");
	}
}
