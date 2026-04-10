package com.miyou.app.infrastructure.memory.adapter

data class MemoryExtractionConfig(
    val model: String,
    val conversationThreshold: Int,
    val importanceBoost: Float,
    val importanceThreshold: Float,
)
