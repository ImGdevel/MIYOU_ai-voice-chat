package com.miyou.app.domain.credit.exception

class UnsupportedPaymentProviderException(
    val pgProvider: String,
) : RuntimeException("Unsupported payment provider: $pgProvider")
