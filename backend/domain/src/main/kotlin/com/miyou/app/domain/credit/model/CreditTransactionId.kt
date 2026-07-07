package com.miyou.app.domain.credit.model

import java.util.UUID

data class CreditTransactionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "creditTransactionId cannot be null or blank" }
    }

    companion object {
        @JvmStatic
        fun of(value: String): CreditTransactionId = CreditTransactionId(value)

        @JvmStatic
        fun generate(): CreditTransactionId = CreditTransactionId(UUID.randomUUID().toString())
    }

    fun value(): String = value
}
