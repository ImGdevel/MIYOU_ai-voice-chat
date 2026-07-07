package com.miyou.app.infrastructure.memory.adapter

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.miyou.app.domain.dialogue.model.CompletionRequest
import com.miyou.app.domain.dialogue.model.Message
import com.miyou.app.domain.dialogue.port.LlmPort
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

    private fun getSystemPrompt(): String =
        """
        You are a memory extraction system. Analyze conversations and extract meaningful memories.

        Extract two types of memories:
        1. EXPERIENTIAL: Personal experiences, events, activities the user has done or plans to do
        2. FACTUAL: Facts about the user (preferences, beliefs, relationships, skills)

        Rules:
        - Only extract NEW information not already in existing memories
        - If existing memory needs importance update, output it with new importance
        - Set importance (0.0-1.0): personal/emotional = higher, general facts = lower
        - Provide brief reasoning for each memory
        - The content MUST start with an explicit grammatical subject: "사용자" (the user)
          or "AI" (the persona). Never use a personal name/nickname, and never write a
          subjectless predicate. Write "사용자는 노래 부르는 것을 좋아한다", not "노래
          부르는 것을 좋아함" or a name.
        - If new information CONTRADICTS an existing memory (changed preference, breakup,
          moved away, etc. - not just an importance nudge), set "supersedesMemoryId" to
          that memory's id (shown in "Existing Memories" below) and still write the new
          content normally as a fresh memory. Only use this for genuine contradictions,
          not minor updates. Omit the field (or use null) when there is no contradiction.
        - Set "emotion" (one of NEUTRAL/POSITIVE/NEGATIVE/SHOCKING): how emotionally
          charged the memory is, INDEPENDENT of importance. SHOCKING = traumatic or
          deeply surprising events that should never be forgotten even if rarely
          revisited. Most everyday facts are NEUTRAL - reserve POSITIVE/NEGATIVE/SHOCKING
          for genuinely emotional content.

        Output ONLY valid JSON array:
        [
        {
            "type": "EXPERIENTIAL",
            "content": "clear, concise memory statement",
            "importance": 0.8,
            "reasoning": "why this matters",
            "supersedesMemoryId": null,
            "emotion": "NEUTRAL"
        }
        ]

        Return empty array [] if no new memories to extract.
        """.trimIndent()

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
}
