package com.miyou.app.api.credit.dto

import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class ChargeByPaymentRequest(
    @field:Size(max = 128, message = "userId too long")
    val userId: String?,
    val paymentKey: String,
    val orderId: String,
    val pgProvider: String,
    @field:Positive val amount: Long,
)
