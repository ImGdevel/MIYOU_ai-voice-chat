package com.miyou.app.api.credit.dto

import com.miyou.app.domain.credit.model.UserCredit

data class UserCreditResponse(
    val userId: String,
    val balance: Long,
) {
    companion object {
        fun from(credit: UserCredit): UserCreditResponse = UserCreditResponse(credit.userId, credit.balance)
    }
}
