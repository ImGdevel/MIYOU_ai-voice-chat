package com.miyou.app.domain.credit.model

import java.time.Instant

data class CreditTransaction(
    val transactionId: CreditTransactionId,
    val userId: String,
    val type: CreditTransactionType,
    val source: CreditSource,
    val amount: Long,
    val balanceBefore: Long,
    val balanceAfter: Long,
    val referenceId: String?,
    val createdAt: Instant? = null,
) {
    init {
        require(amount > 0) { "amount must be positive" }
    }

    fun withCreatedAtOrNow(): CreditTransaction = if (createdAt == null) copy(createdAt = Instant.now()) else this

    companion object {
        @JvmStatic
        fun of(
            userId: String,
            type: CreditTransactionType,
            source: CreditSource,
            amount: Long,
            balanceBefore: Long,
            balanceAfter: Long,
            referenceId: String? = null,
        ): CreditTransaction =
            CreditTransaction(
                CreditTransactionId.generate(),
                userId,
                type,
                source,
                amount,
                balanceBefore,
                balanceAfter,
                referenceId = referenceId,
            ).withCreatedAtOrNow()
    }
}
