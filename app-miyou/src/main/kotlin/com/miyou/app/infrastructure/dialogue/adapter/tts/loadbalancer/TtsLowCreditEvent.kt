package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import java.time.Instant

data class TtsLowCreditEvent(
    val endpointId: String,
    val remainingCredits: Double,
    val threshold: Double,
    val occurredAt: Instant = Instant.now(),
)
