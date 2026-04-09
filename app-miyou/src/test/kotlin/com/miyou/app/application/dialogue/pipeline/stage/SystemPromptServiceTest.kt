package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.dialogue.policy.PromptTemplatePolicy
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.port.TemplateLoaderPort
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.domain.retrieval.model.RetrievalDocument
import com.miyou.app.fixture.ConversationSessionFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class SystemPromptServiceTest {
    @Mock
    private lateinit var templateLoader: TemplateLoaderPort

    @Test
    @DisplayName("시스템 프롬프트를 구성된 모든 섹션으로 렌더링한다")
    fun buildSystemPrompt_shouldRenderBaseTemplateWithAllSections() {
        `when`(templateLoader.load("system/persona/maid")).thenReturn("persona")

        val policy =
            PromptTemplatePolicy(
                "{{persona}}\n\n{{common}}\n\n{{memories}}\n\n{{context}}",
                "",
                "common",
                "",
            )

        val service = SystemPromptService(templateLoader, policy)
        val sessionId = ConversationSessionFixture.createId()
        val experiential = Memory.create(sessionId, MemoryType.EXPERIENTIAL, "user likes sushi", 0.8f)
        val factual = Memory.create(sessionId, MemoryType.FACTUAL, "user is a developer", 0.9f)
        val memories = MemoryRetrievalResult.of(listOf(experiential), listOf(factual))
        val context =
            RetrievalContext.of(
                "user preference",
                listOf(RetrievalDocument.of("sushi improves stamina", 90)),
            )

        val prompt = service.buildSystemPrompt(PersonaId.of("maid"), context, memories)

        assertThat(prompt)
            .contains("persona")
            .contains("common")
            .contains("- user likes sushi")
            .contains("- user is a developer")
            .contains("sushi improves stamina")
    }

    @Test
    @DisplayName("페르소나 템플릿이 없으면 설정된 기본 프롬프트로 대체한다")
    fun buildSystemPrompt_shouldFallbackToConfiguredSystemPromptWhenPersonaTemplateIsMissing() {
        `when`(templateLoader.load("system/persona/maid"))
            .thenThrow(RuntimeException("persona missing"))

        val policy = PromptTemplatePolicy("", "", "common", "configured persona")
        val service = SystemPromptService(templateLoader, policy)

        val prompt =
            service.buildSystemPrompt(
                PersonaId.of("maid"),
                RetrievalContext.empty("query"),
                MemoryRetrievalResult.empty(),
            )

        assertThat(prompt).isEqualTo("configured persona\n\ncommon")
    }

    @Test
    @DisplayName("정책 템플릿이 비어 있어도 시스템 프롬프트를 생성한다")
    fun buildSystemPrompt_shouldHandleBlankPolicyTemplates() {
        val policy = PromptTemplatePolicy("", "", "", " configured persona ")
        val service = SystemPromptService(templateLoader, policy)

        val prompt =
            service.buildSystemPrompt(
                PersonaId.defaultPersona(),
                RetrievalContext.empty("query"),
                MemoryRetrievalResult.empty(),
            )

        assertThat(prompt).isEqualTo("configured persona")
    }
}
