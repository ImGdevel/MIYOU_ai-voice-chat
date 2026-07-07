package com.miyou.app.domain.dialogue.port

import reactor.core.publisher.Mono

interface DialogueCreditChargingPort {
    fun deduct(command: CreditDeductCommand): Mono<CreditDeductResult>

    fun refund(command: CreditRefundCommand): Mono<CreditRefundResult>
}

data class CreditDeductCommand(
    val userId: String,
    val sessionId: String,
)

data class CreditDeductResult(
    val transactionId: String,
)

data class CreditRefundCommand(
    val userId: String,
    val sessionId: String,
)

data class CreditRefundResult(
    val transactionId: String,
)
