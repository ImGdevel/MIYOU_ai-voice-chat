package com.miyou.app.application.memory.policy

data class MemoryRetrievalPolicy(
    val importanceBoost: Float,
    val importanceThreshold: Float,
    val associativeHopEnabled: Boolean = false,
    val associativeHopTopK: Int = 5,
    val associativeHopMinScore: Float = 0.5f,
    val recencyWeight: Float = 0.1f,
    val candidateMultiplier: Int = 2,
)
