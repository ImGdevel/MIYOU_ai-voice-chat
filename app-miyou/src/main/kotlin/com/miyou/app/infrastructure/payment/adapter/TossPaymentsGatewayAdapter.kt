package com.miyou.app.infrastructure.payment.adapter

import com.miyou.app.infrastructure.payment.port.PaymentGatewayPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
@ConditionalOnProperty(name = ["payment.toss.enabled"], havingValue = "true", matchIfMissing = false)
class TossPaymentsGatewayAdapter : PaymentGatewayPort {
    private val log = KotlinLogging.logger {}

    override fun getProviderName(): String = "toss"

    override fun confirmPayment(
        request: PaymentGatewayPort.PaymentConfirmRequest,
    ): Mono<PaymentGatewayPort.PaymentConfirmResult> {
        log.warn { "TossPaymentsGatewayAdapter is running in stub mode. paymentKey=${request.paymentKey}" }
        return Mono.just(
            PaymentGatewayPort.PaymentConfirmResult(
                request.paymentKey,
                request.orderId,
                request.amount,
                "DONE",
            ),
        )
    }
}
