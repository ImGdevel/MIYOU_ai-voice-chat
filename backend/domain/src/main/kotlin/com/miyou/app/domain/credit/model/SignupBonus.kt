package com.miyou.app.domain.credit.model

object SignupBonus : CreditSource {
    override fun sourceType(): CreditSourceType = CreditSourceType.SIGNUP_BONUS
}
