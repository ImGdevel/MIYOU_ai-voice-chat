package com.miyou.app.application.memory.policy

data class MemoryCuratorPolicy(
    val decayRateHigh: Float,
    val decayRateLow: Float,
    val decayExemptThreshold: Float,
    val archiveImportanceThreshold: Float,
    val archiveIdleDays: Long,
)
