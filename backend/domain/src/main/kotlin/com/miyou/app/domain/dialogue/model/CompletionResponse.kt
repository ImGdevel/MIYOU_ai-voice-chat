package com.miyou.app.domain.dialogue.model

data class CompletionResponse(
    val content: String,
    val model: String?,
    val tokensUsed: Int,
) {
    companion object {
        fun of(content: String): CompletionResponse = CompletionResponse(content, null, 0)
    }
}
