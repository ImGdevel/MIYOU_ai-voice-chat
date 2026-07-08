package com.miyou.app.monitoring.port

interface ConversationMetricsPort {
    fun recordConversationIncrement()

    fun recordConversationReset()

    fun recordQueryLength(length: Int)

    fun recordResponseLength(length: Int)

    fun recordConversationCount(count: Long)

    fun recordFormatViolation()
}
