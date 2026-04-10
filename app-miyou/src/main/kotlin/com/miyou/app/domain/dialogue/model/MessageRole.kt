package com.miyou.app.domain.dialogue.model

enum class MessageRole(
    val value: String,
) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
}
