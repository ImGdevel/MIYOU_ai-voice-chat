package com.miyou.app.infrastructure.monitoring.config

import com.miyou.app.application.monitoring.port.ConversationMetricsPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class ConversationMetricsConfiguration(
    private val meterRegistry: MeterRegistry,
) : ConversationMetricsPort {
    private val conversationIncrementCounter: Counter =
        Counter
            .builder("conversation.increment.count")
            .description("Number of times conversation counter was incremented")
            .register(meterRegistry)

    private val conversationResetCounter: Counter =
        Counter
            .builder("conversation.reset.count")
            .description("Number of times conversation counter was reset")
            .register(meterRegistry)

    private val queryLengthSummary: DistributionSummary =
        DistributionSummary
            .builder("conversation.query.length")
            .description("Distribution of user query lengths in characters")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    private val responseLengthSummary: DistributionSummary =
        DistributionSummary
            .builder("conversation.response.length")
            .description("Distribution of assistant response lengths in characters")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    private val conversationCountSummary: DistributionSummary =
        DistributionSummary
            .builder("conversation.count.distribution")
            .description("Distribution of conversation counts per user")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry)

    override fun recordConversationIncrement() {
        conversationIncrementCounter.increment()
    }

    override fun recordConversationReset() {
        conversationResetCounter.increment()
    }

    override fun recordQueryLength(length: Int) {
        queryLengthSummary.record(length.toDouble())
    }

    override fun recordResponseLength(length: Int) {
        responseLengthSummary.record(length.toDouble())
    }

    override fun recordConversationCount(count: Long) {
        conversationCountSummary.record(count.toDouble())
    }

    fun recordConversationByType(type: String) {
        Counter
            .builder("conversation.by_type")
            .tag("type", type)
            .description("Number of conversations by type")
            .register(meterRegistry)
            .increment()
    }
}
