package com.miyou.app.infrastructure.memory.adapter

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.miyou.app.domain.dialogue.model.CompletionRequest
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.Message
import com.miyou.app.domain.dialogue.port.LlmPort
import com.miyou.app.domain.memory.model.ExtractedMemory
import com.miyou.app.domain.memory.model.MemoryExtractionContext
import com.miyou.app.domain.memory.port.MemoryExtractionPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class LlmMemoryExtractionAdapter(
    private val llmPort: LlmPort,
    private val objectMapper: ObjectMapper,
    config: MemoryExtractionConfig,
) : MemoryExtractionPort {
    private val log = LoggerFactory.getLogger(LlmMemoryExtractionAdapter::class.java)
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
            .flatMapMany { response -> parseExtractedMemories(context.sessionId, response) }
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

        Output ONLY valid JSON array:
        [
        {
            "type": "EXPERIENTIAL",
            "content": "clear, concise memory statement",
            "importance": 0.8,
            "reasoning": "why this matters"
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
                    .append("- [")
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
        sessionId: ConversationSessionId,
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
            Flux.fromIterable(dtos).map { dto -> dto.toExtractedMemory(sessionId) }
        } catch (e: Exception) {
            log.warn("Failed to parse memory extraction response: $jsonResponse", e)
            Flux.empty()
        }
}
