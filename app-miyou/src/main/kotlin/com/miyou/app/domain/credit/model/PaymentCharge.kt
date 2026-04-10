package com.miyou.app.domain.credit.model

data class PaymentCharge(
    val paymentId: String,
    val pgProvider: String,
) : CreditSource {
    override fun sourceType(): CreditSourceType = CreditSourceType.PAYMENT_CHARGE

    fun paymentId(): String = paymentId

    fun pgProvider(): String = pgProvider
}
