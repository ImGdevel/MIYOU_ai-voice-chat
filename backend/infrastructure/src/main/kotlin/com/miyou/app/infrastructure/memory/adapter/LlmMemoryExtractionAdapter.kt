package com.miyou.app.infrastructure.memory.adapter

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.miyou.app.domain.dialogue.model.CompletionRequest
import com.miyou.app.domain.dialogue.model.Message
import com.miyou.app.domain.dialogue.port.LlmPort
import com.miyou.app.domain.dialogue.port.TemplateLoaderPort
import com.miyou.app.domain.memory.model.ExtractedMemory
import com.miyou.app.domain.memory.model.MemoryExtractionContext
import com.miyou.app.domain.memory.port.MemoryExtractionPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class LlmMemoryExtractionAdapter(
    private val llmPort: LlmPort,
    private val objectMapper: ObjectMapper,
    private val templateLoader: TemplateLoaderPort,
    config: MemoryExtractionConfig,
) : MemoryExtractionPort {
    private val log = KotlinLogging.logger {}
    private val extractionModel = config.model

    override fun extractMemories(context: MemoryExtractionContext): Flux<ExtractedMemory> {
        val prompt = buildExtractionPrompt(context)

        val messages =
            listOf(
                Message.system(getSystemPrompt()),
                Message.user(prompt),
            )
        val request = CompletionRequest.withMessages(messages, extractionModel, false)

        return llmPort
            .complete(request)
            .flatMapMany { response -> parseExtractedMemories(context, response) }
    }

    private fun getSystemPrompt(): String = templateLoader.load(MEMORY_EXTRACTION_SYSTEM_TEMPLATE)

    private fun buildExtractionPrompt(context: MemoryExtractionContext): String {
        val prompt = StringBuilder("Recent Conversations:\n")
        context.recentConversations.forEach { turn ->
            prompt.append("User: ").append(turn.query).append('\n')
            turn.response?.let { prompt.append("Assistant: ").append(it).append('\n') }
        }

        if (context.existingMemories.isNotEmpty()) {
            prompt.append("\nExisting Memories (for deduplication):\n")
            context.existingMemories.forEach { memory ->
                val importance = memory.importance?.let { "%.2f".format(it) } ?: "N/A"
                prompt
                    .append("- [id: ")
                    .append(memory.id)
                    .append(", ")
                    .append(memory.type)
                    .append(", importance: ")
                    .append(importance)
                    .append("] ")
                    .append(memory.content)
                    .append('\n')
            }
        }

        return prompt.toString()
    }

    private fun parseExtractedMemories(
        context: MemoryExtractionContext,
        jsonResponse: String,
    ): Flux<ExtractedMemory> =
        try {
            var cleaned = jsonResponse.trim()
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7)
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length - 3)
            }
            cleaned = cleaned.trim()

            val dtos =
                objectMapper.readValue(
                    cleaned,
                    object : TypeReference<List<MemoryExtractionDto>>() {},
                )
            val existingIds = context.existingMemories.mapNotNull { it.id }.toSet()
            Flux
                .fromIterable(dtos)
                .map { dto -> dto.toExtractedMemory(context.sessionId) }
                .map { extracted -> validateSupersedesTarget(extracted, existingIds) }
        } catch (e: Exception) {
            log.warn(e) { "Failed to parse memory extraction response: $jsonResponse" }
            Flux.empty()
        }

    private fun validateSupersedesTarget(
        extracted: ExtractedMemory,
        existingIds: Set<String>,
    ): ExtractedMemory {
        val targetId = extracted.supersedesMemoryId ?: return extracted
        if (targetId in existingIds) {
            return extracted
        }
        log.warn { "supersedesMemoryId가 컨텍스트에 없는 id를 가리켜 무시함: $targetId" }
        return extracted.copy(supersedesMemoryId = null)
    }

    private companion object {
        const val MEMORY_EXTRACTION_SYSTEM_TEMPLATE = "memory/extraction-system"
    }
}
