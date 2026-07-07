package com.miyou.app.infrastructure.dialogue.adapter.llm

import com.miyou.app.domain.dialogue.model.CompletionRequest
import com.miyou.app.domain.dialogue.model.Message
import com.miyou.app.domain.dialogue.model.TokenUsage
import com.miyou.app.domain.dialogue.port.LlmPort
import com.miyou.app.domain.dialogue.port.TokenUsageProvider
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * 토큰 사용량을 추적하는 LLM 호출 어댑터.
 */
@Primary
@Component
class TokenAwareLlmAdapter(
    private val chatModel: ChatModel,
) : LlmPort,
    TokenUsageProvider {
    private val usageByCorrelation = ConcurrentHashMap<String, AtomicReference<TokenUsage>>()

    override fun streamCompletion(request: CompletionRequest): Flux<String> {
        val messages = convertMessages(request.messages())
        val options =
            OpenAiChatOptions
                .builder()
                .model(request.model())
                .streamUsage(true)
                .build()
        val prompt = Prompt(messages, options)

        return chatModel
            .stream(prompt)
            .doOnNext { response ->
                val usage = response.metadata?.usage
                if (usage != null) {
                    val promptTokens = usage.promptTokens
                    val generationTokens = usage.generationTokens
                    if (promptTokens != null && generationTokens != null &&
                        promptTokens <= Int.MAX_VALUE && generationTokens <= Int.MAX_VALUE
                    ) {
                        updateUsage(request, promptTokens.toInt(), generationTokens.toInt())
                    }
                }
            }.mapNotNull { response ->
                val generation = response.result
                generation?.output?.content
            }
    }

    override fun complete(request: CompletionRequest): Mono<String> {
        val messages = convertMessages(request.messages())
        val prompt = Prompt(messages)
        return Mono
            .fromCallable {
                val response = chatModel.call(prompt)
                val usage = response.metadata?.usage
                if (usage != null) {
                    val promptTokens = usage.promptTokens
                    val generationTokens = usage.generationTokens
                    if (promptTokens != null && generationTokens != null &&
                        promptTokens <= Int.MAX_VALUE && generationTokens <= Int.MAX_VALUE
                    ) {
                        updateUsage(request, promptTokens.toInt(), generationTokens.toInt())
                    }
                }

                val result = response.result
                if (result == null || result.output == null) {
                    throw IllegalStateException("Invalid response from LLM")
                }
                result.output.content
            }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getTokenUsage(correlationId: String): TokenUsage? {
        if (correlationId.isBlank()) {
            return null
        }
        val ref = usageByCorrelation.remove(correlationId)
        return ref?.get()
    }

    private fun updateUsage(
        request: CompletionRequest,
        promptTokens: Int,
        completionTokens: Int,
    ) {
        val correlationId = request.additionalParams().getOrDefault("correlationId", "").toString()
        if (correlationId.isNotBlank()) {
            usageByCorrelation
                .computeIfAbsent(correlationId) { AtomicReference(TokenUsage.zero()) }
                .set(TokenUsage.of(promptTokens, completionTokens))
        }
    }

    private fun convertMessages(messages: List<Message>): List<org.springframework.ai.chat.messages.Message> =
        messages.map(::convertMessage)

    private fun convertMessage(message: Message): org.springframework.ai.chat.messages.Message =
        when (message.role()) {
            com.miyou.app.domain.dialogue.model.MessageRole.SYSTEM -> SystemMessage(message.content())
            com.miyou.app.domain.dialogue.model.MessageRole.USER -> UserMessage(message.content())
            com.miyou.app.domain.dialogue.model.MessageRole.ASSISTANT -> AssistantMessage(message.content())
        }
}
