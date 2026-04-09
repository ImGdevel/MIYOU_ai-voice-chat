package com.miyou.app.domain.credit.model

sealed interface CreditSource {
    fun sourceType(): CreditSourceType
}
