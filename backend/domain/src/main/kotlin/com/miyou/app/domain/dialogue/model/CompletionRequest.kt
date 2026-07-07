package com.miyou.app.domain.dialogue.model

import java.util.Locale

data class CompletionRequest(
    val messages: List<Message>,
    val model: String,
    val stream: Boolean,
    val additionalParams: Map<String, Any> = emptyMap(),
) {
    init {
        require(messages.isNotEmpty()) { "messages cannot be null or empty" }
        require(model.isNotBlank()) { "model cannot be null or blank" }
    }

    companion object {
        @JvmStatic
        fun fromPrompt(
            prompt: String,
            model: String,
        ): CompletionRequest = CompletionRequest(listOf(Message.user(prompt)), model, stream = false, emptyMap())

        @JvmStatic
        fun streaming(
            prompt: String,
            model: String,
        ): CompletionRequest = CompletionRequest(listOf(Message.user(prompt)), model, stream = true, emptyMap())

        @JvmStatic
        fun withMessages(
            messages: List<Message>,
            model: String,
            stream: Boolean,
        ): CompletionRequest = CompletionRequest(messages, model, stream, emptyMap())
    }

    fun messages(): List<Message> = messages

    fun model(): String = model

    fun additionalParams(): Map<String, Any> = additionalParams
}
