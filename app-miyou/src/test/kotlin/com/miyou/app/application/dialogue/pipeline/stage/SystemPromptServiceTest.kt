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
    @DisplayName("시스템 프롬프트 생성 시 모든 섹션을 포함한다")
    fun buildSystemPrompt_shouldRenderBaseTemplateWithAllSections() {
        `when`(templateLoader.load("system/persona/maid")).thenReturn("persona")

        val policy =
            PromptTemplatePolicy(
                "{{persona}}\n\n{{common}}\n\n{{memories}}\n\n{{context}}",
                "",
                "common",
                null,
            )

        val service = SystemPromptService(templateLoader, policy)
        val sessionId = ConversationSessionFixture.createId()
        val experiential = Memory.create(sessionId, MemoryType.EXPERIENTIAL, "사용자는 러닝을 좋아한다.", 0.8f)
        val factual = Memory.create(sessionId, MemoryType.FACTUAL, "사용자의 직업은 개발자다.", 0.9f)
        val memories = MemoryRetrievalResult.of(listOf(experiential), listOf(factual))
        val context =
            RetrievalContext.of(
                "사용자 취미",
                listOf(RetrievalDocument.of("러닝은 체력 향상에 도움이 된다.", 90)),
            )

        val prompt = service.buildSystemPrompt(PersonaId.of("maid"), context, memories)

        assertThat(prompt)
            .contains("persona")
            .contains("common")
            .contains("대화 상대에 대한 기억:")
            .contains("경험적 기억:")
            .contains("사실 기반 기억:")
            .contains("- 사용자는 러닝을 좋아한다.")
            .contains("- 사용자의 직업은 개발자다.")
            .contains("참고 정보:")
            .contains("러닝은 체력 향상에 도움이 된다.")
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
    @DisplayName("정책 템플릿이 null이어도 시스템 프롬프트를 생성한다")
    fun buildSystemPrompt_shouldHandleNullPolicyTemplates() {
        val policy = PromptTemplatePolicy(null, null, null, " configured persona ")
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
