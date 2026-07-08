package com.miyou.app.domain.credit.exception

import com.miyou.app.exception.BusinessException

class InsufficientCreditException(
    val userId: String,
    val currentBalance: Long,
    val requiredAmount: Long,
) : BusinessException(
        errorCode = CreditErrorCode.INSUFFICIENT_CREDIT,
        message =
            "${CreditErrorCode.INSUFFICIENT_CREDIT.message} " +
                "(userId=$userId, currentBalance=$currentBalance, requiredAmount=$requiredAmount)",
        details =
            mapOf(
                "userId" to userId,
                "currentBalance" to currentBalance,
                "requiredAmount" to requiredAmount
            )
    )
