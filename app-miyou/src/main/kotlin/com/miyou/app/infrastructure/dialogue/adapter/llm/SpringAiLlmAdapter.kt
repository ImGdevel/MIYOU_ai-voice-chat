package com.miyou.app.infrastructure.dialogue.adapter.llm

import com.miyou.app.domain.dialogue.model.CompletionRequest
import com.miyou.app.domain.dialogue.model.Message
import com.miyou.app.domain.dialogue.port.LlmPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Spring AI OpenAI LLM 호출 어댑터.
 */
@Component
class SpringAiLlmAdapter(
    chatModel: ChatModel,
) : LlmPort {
    private val chatClient = ChatClient.builder(chatModel).build()

    override fun streamCompletion(request: CompletionRequest): Flux<String> {
        val springAiMessages = convertMessages(request.messages())
        return chatClient
            .prompt(Prompt(springAiMessages))
            .stream()
            .content()
    }

    override fun complete(request: CompletionRequest): Mono<String> {
        val springAiMessages = convertMessages(request.messages())
        return Mono
            .fromCallable { chatClient.prompt(Prompt(springAiMessages)).call().content() }
            .subscribeOn(Schedulers.boundedElastic())
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
