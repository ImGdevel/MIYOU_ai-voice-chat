package com.miyou.app.application.monitoring.port

interface ConversationMetricsPort {
    fun recordConversationIncrement()

    fun recordConversationReset()

    fun recordQueryLength(length: Int)

    fun recordResponseLength(length: Int)

    fun recordConversationCount(count: Long)
}
