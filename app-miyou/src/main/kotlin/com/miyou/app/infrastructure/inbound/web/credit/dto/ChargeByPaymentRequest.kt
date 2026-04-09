package com.miyou.app.infrastructure.inbound.web.credit.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class ChargeByPaymentRequest(
    val userId: String,
    val paymentKey: String,
    val orderId: String,
    val pgProvider: String,
    @field:Positive val amount: Long,
)
