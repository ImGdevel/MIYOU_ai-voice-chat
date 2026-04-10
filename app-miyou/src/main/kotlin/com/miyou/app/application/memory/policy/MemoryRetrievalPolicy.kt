package com.miyou.app.application.memory.policy

data class MemoryRetrievalPolicy(
    val importanceBoost: Float,
    val importanceThreshold: Float,
)
