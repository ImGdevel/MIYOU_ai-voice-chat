package com.miyou.app.domain.dialogue.model

data class Message(
    val role: MessageRole,
    val content: String,
) {
    init {
        require(content.isNotBlank()) { "content cannot be null or blank" }
    }

    companion object {
        fun user(content: String): Message = Message(MessageRole.USER, content)

        fun system(content: String): Message = Message(MessageRole.SYSTEM, content)

        fun assistant(content: String): Message = Message(MessageRole.ASSISTANT, content)
    }

    fun role(): MessageRole = role

    fun content(): String = content
}
