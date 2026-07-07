package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.circuit

enum class CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN,
}
