package com.miyou.app.infrastructure.payment.port

import reactor.core.publisher.Mono

interface PaymentGatewayPort {
    fun getProviderName(): String

    fun confirmPayment(request: PaymentConfirmRequest): Mono<PaymentConfirmResult>

    data class PaymentConfirmRequest(
        val paymentKey: String,
        val orderId: String,
        val amount: Long,
    )

    data class PaymentConfirmResult(
        val paymentId: String,
        val orderId: String,
        val amount: Long,
        val status: String,
    )
}
