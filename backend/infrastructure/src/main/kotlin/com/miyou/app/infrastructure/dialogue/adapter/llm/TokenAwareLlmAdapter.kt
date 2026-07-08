package com.miyou.app.infrastructure.dialogue.adapter.llm

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import reactor.core.publisher.SignalType
import reactor.core.scheduler.Schedulers
import java.time.Duration

/**
 * 토큰 사용량을 추적하는 LLM 호출 어댑터.
 */
@Primary
@Component
class TokenAwareLlmAdapter(
    private val chatModel: ChatModel,
) : LlmPort,
    TokenUsageProvider {
    /**
     * correlationId별 토큰 사용량 캐시.
     *
     * 정상 완료 시 getTokenUsage()에서, 에러/취소 시 doFinally에서 각각 항목을 지우지만,
     * 호출부가 getTokenUsage()를 아예 호출하지 않는 경로(다운스트림 오류, 클라이언트
     * 조기 종료 등)까지 대비해 TTL 만료를 걸어 무한 누적을 막는다.
     */
    private val usageByCorrelation: Cache<String, TokenUsage> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build()

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
            }.doFinally { signalType ->
                // 정상 완료 시에는 getTokenUsage()의 remove-on-read로 정리되지만,
                // 에러/취소로 끝나면 그 경로를 절대 못 타므로 여기서 확실히 제거해 맵 누수를 막는다.
                if (signalType == SignalType.ON_ERROR || signalType == SignalType.CANCEL) {
                    correlationIdOf(request)?.let { usageByCorrelation.invalidate(it) }
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
        val usage = usageByCorrelation.getIfPresent(correlationId)
        usageByCorrelation.invalidate(correlationId)
        return usage
    }

    private fun updateUsage(
        request: CompletionRequest,
        promptTokens: Int,
        completionTokens: Int,
    ) {
        val correlationId = correlationIdOf(request) ?: return
        usageByCorrelation.put(correlationId, TokenUsage.of(promptTokens, completionTokens))
    }

    private fun correlationIdOf(request: CompletionRequest): String? {
        val correlationId = request.additionalParams().getOrDefault("correlationId", "").toString()
        return correlationId.ifBlank { null }
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
