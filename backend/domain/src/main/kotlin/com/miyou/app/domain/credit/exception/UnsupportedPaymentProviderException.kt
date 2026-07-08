package com.miyou.app.domain.credit.exception

import com.miyou.app.exception.BusinessException

class UnsupportedPaymentProviderException(
    val pgProvider: String,
) : BusinessException(
        errorCode = CreditErrorCode.UNSUPPORTED_PAYMENT_PROVIDER,
        message = "${CreditErrorCode.UNSUPPORTED_PAYMENT_PROVIDER.message} (pgProvider=$pgProvider)",
        details = mapOf("pgProvider" to pgProvider)
    )
