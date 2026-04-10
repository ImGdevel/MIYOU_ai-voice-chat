package com.miyou.app.domain.dialogue.service

import reactor.core.publisher.Flux

class SentenceAssembler {
    fun assemble(tokenStream: Flux<String>): Flux<String> =
        tokenStream
            .bufferUntil { isSentenceEnd(it) }
            .filter { it.isNotEmpty() }
            .map { joinTokensToSentence(it) }

    private fun isSentenceEnd(token: String): Boolean {
        if (token.isBlank()) {
            return false
        }
        val trimmed = token.trim()
        return trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")
    }

    private fun joinTokensToSentence(tokens: List<String>): String = tokens.joinToString(separator = "").trim()
}
