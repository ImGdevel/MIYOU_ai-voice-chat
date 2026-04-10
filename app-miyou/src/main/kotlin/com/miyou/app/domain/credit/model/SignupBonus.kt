package com.miyou.app.domain.credit.model

class SignupBonus : CreditSource {
    override fun sourceType(): CreditSourceType = CreditSourceType.SIGNUP_BONUS
}
