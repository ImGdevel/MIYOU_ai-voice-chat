package com.miyou.app.domain.dialogue.model

import java.util.UUID

data class ConversationSessionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "sessionId cannot be null or blank" }
        require(value.length <= 128) { "sessionId too long" }
    }

    companion object {
        @JvmStatic
        fun of(value: String): ConversationSessionId = ConversationSessionId(value)

        @JvmStatic
        fun generate(): ConversationSessionId = ConversationSessionId(UUID.randomUUID().toString())
    }

    fun value(): String = value
}
