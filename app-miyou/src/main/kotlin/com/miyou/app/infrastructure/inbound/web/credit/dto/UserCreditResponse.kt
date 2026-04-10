package com.miyou.app.infrastructure.inbound.web.credit.dto

import com.miyou.app.domain.credit.model.UserCredit

data class UserCreditResponse(
    val userId: String,
    val balance: Long,
) {
    companion object {
        fun from(credit: UserCredit): UserCreditResponse = UserCreditResponse(credit.userId.value, credit.balance)
    }
}
