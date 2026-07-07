package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import java.time.Instant

data class TtsEndpointFailureEvent(
    val endpointId: String,
    val errorType: String,
    val errorMessage: String,
    val occurredAt: Instant = Instant.now(),
) {
    init {
        require(endpointId.isNotBlank()) { "endpointId must not be blank" }
        require(errorType.isNotBlank()) { "errorType must not be blank" }
        require(errorMessage.isNotBlank()) { "errorMessage must not be blank" }
    }
}
