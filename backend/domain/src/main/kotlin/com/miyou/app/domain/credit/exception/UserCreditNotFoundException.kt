package com.miyou.app.domain.credit.exception

import com.miyou.app.exception.BusinessException

class UserCreditNotFoundException(
    val userId: String,
) : BusinessException(
        errorCode = CreditErrorCode.USER_CREDIT_NOT_FOUND,
        message = "${CreditErrorCode.USER_CREDIT_NOT_FOUND.message} (userId=$userId)",
        details = mapOf("userId" to userId)
    )
