package com.miyou.app.domain.dialogue.model

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
) {
    companion object {
        fun zero(): TokenUsage = TokenUsage(0, 0, 0)

        fun of(
            promptTokens: Int,
            completionTokens: Int,
        ): TokenUsage = TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens)
    }
}
