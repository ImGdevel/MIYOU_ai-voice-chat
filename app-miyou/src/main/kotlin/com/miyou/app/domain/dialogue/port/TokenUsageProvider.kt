package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.TokenUsage

interface TokenUsageProvider {
    fun getTokenUsage(correlationId: String): TokenUsage?
}
