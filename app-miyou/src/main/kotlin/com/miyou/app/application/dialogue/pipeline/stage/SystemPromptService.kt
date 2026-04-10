package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.dialogue.policy.PromptTemplatePolicy
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.port.TemplateLoaderPort
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.StringJoiner

@Service
class SystemPromptService(
    private val templateLoader: TemplateLoaderPort,
    private val policy: PromptTemplatePolicy,
) {
    fun buildSystemPrompt(
        personaId: PersonaId,
        context: RetrievalContext,
        memories: MemoryRetrievalResult,
    ): String {
        val sections =
            PromptSections(
                resolvePersonaPrompt(personaId),
                policy.commonTemplate,
                buildMemoryBlock(memories),
                buildContextBlock(context),
            )

        val baseTemplate = policy.baseTemplate
        if (!baseTemplate.isBlank()) {
            return applyTemplate(baseTemplate, sections)
        }
        return joinNonBlankBlocks(sections.persona, sections.common, sections.memories, sections.context)
    }

    fun buildSystemPrompt(
        context: RetrievalContext,
        memories: MemoryRetrievalResult,
    ): String = buildSystemPrompt(PersonaId.defaultPersona(), context, memories)

    private fun resolvePersonaPrompt(personaId: PersonaId): String {
        if (personaId.value != PersonaId.DEFAULT.value) {
            val dynamicTemplate = loadPersonaTemplate(personaId)
            if (dynamicTemplate.isNotBlank()) {
                return dynamicTemplate
            }
        }
        return resolveDefaultPersonaPrompt()
    }

    private fun resolveDefaultPersonaPrompt(): String {
        val defaultPersonaTemplate = policy.defaultPersonaTemplate
        if (!defaultPersonaTemplate.isBlank()) {
            return defaultPersonaTemplate
        }
        val configuredSystemPrompt = policy.configuredSystemPrompt
        if (configuredSystemPrompt.isNotBlank()) {
            return configuredSystemPrompt.trim()
        }
        return ""
    }

    private fun loadPersonaTemplate(personaId: PersonaId): String {
        val templatePath = "$PERSONA_TEMPLATE_PREFIX${personaId.value}"
        return try {
            templateLoader.load(templatePath).trim()
        } catch (exception: RuntimeException) {
            logger.warn("Persona template load failed: {} ({})", templatePath, exception.message)
            ""
        }
    }

    private fun buildMemoryBlock(memories: MemoryRetrievalResult): String {
        if (memories.isEmpty()) {
            return ""
        }

        val builder = StringBuilder(MEMORIES_TITLE).append('\n')
        appendMemorySection(builder, EXPERIENTIAL_MEMORY_TITLE, memories.experientialMemories)
        appendMemorySection(builder, FACTUAL_MEMORY_TITLE, memories.factualMemories)
        return builder.toString().trim()
    }

    private fun buildContextBlock(context: RetrievalContext): String {
        if (context.isEmpty()) {
            return ""
        }
        val joiner = StringJoiner("\n")
        context.documents
            .map { it.content }
            .filter { !it.isNullOrBlank() }
            .forEach(joiner::add)
        if (joiner.length() == 0) {
            return ""
        }
        return "$CONTEXT_TITLE\n$joiner"
    }

    private fun applyTemplate(
        template: String,
        sections: PromptSections,
    ): String {
        val rendered =
            template
                .replace(PERSONA_PLACEHOLDER, normalizeBlock(sections.persona))
                .replace(COMMON_PLACEHOLDER, normalizeBlock(sections.common))
                .replace(MEMORIES_PLACEHOLDER, normalizeBlock(sections.memories))
                .replace(CONTEXT_PLACEHOLDER, normalizeBlock(sections.context))
        return normalizeTemplateSpacing(rendered)
    }

    private fun joinNonBlankBlocks(vararg blocks: String): String {
        val joiner = StringJoiner("\n\n")
        blocks.forEach { block ->
            if (block.isNotBlank()) {
                joiner.add(block.trim())
            }
        }
        return joiner.toString()
    }

    private fun appendMemorySection(
        builder: StringBuilder,
        sectionTitle: String,
        memories: List<Memory>,
    ) {
        if (memories.isEmpty()) {
            return
        }
        val lines =
            memories
                .mapNotNull { it.content }
                .filter { it.isNotBlank() }
                .map { "- $it" }
                .toList()
        if (lines.isEmpty()) {
            return
        }
        builder.append('\n').append(sectionTitle).append('\n')
        lines.forEach { line ->
            builder.append(line).append('\n')
        }
    }

    private fun normalizeBlock(block: String): String = block.trim()

    private fun normalizeTemplateSpacing(template: String): String {
        var compacted = template.replace(Regex("(?m)^[ \\t]+$"), "")
        compacted = compacted.replace(Regex("\\n{3,}"), "\n\n")
        return compacted.trim()
    }

    private fun normalizeTemplateInput(template: String?): String = template ?: ""

    private data class PromptSections(
        val persona: String,
        val common: String,
        val memories: String,
        val context: String,
    )

    private companion object {
        const val PERSONA_TEMPLATE_PREFIX = "system/persona/"
        const val PERSONA_PLACEHOLDER = "{{persona}}"
        const val COMMON_PLACEHOLDER = "{{common}}"
        const val MEMORIES_PLACEHOLDER = "{{memories}}"
        const val CONTEXT_PLACEHOLDER = "{{context}}"

        const val MEMORIES_TITLE = "기억 데이터:"
        const val EXPERIENTIAL_MEMORY_TITLE = "체험 기억:"
        const val FACTUAL_MEMORY_TITLE = "사실 기억:"
        const val CONTEXT_TITLE = "지금 상황:"

        private val logger by lazy { LoggerFactory.getLogger(SystemPromptService::class.java) }
    }
}
