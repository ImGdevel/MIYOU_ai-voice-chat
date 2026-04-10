package com.miyou.app.domain.credit.model

import com.miyou.app.domain.dialogue.model.UserId
import java.time.Instant

data class CreditTransaction(
    val transactionId: CreditTransactionId,
    val userId: UserId,
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
            userId: UserId,
            type: CreditTransactionType,
            source: CreditSource,
            amount: Long,
            balanceBefore: Long,
            balanceAfter: Long,
        ): CreditTransaction =
            CreditTransaction(
                CreditTransactionId.generate(),
                userId,
                type,
                source,
                amount,
                balanceBefore,
                balanceAfter,
                referenceId = null,
            ).withCreatedAtOrNow()

        @JvmStatic
        fun of(
            userId: UserId,
            type: CreditTransactionType,
            source: CreditSource,
            amount: Long,
            balanceBefore: Long,
            balanceAfter: Long,
            referenceId: String,
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

    fun transactionId(): CreditTransactionId = transactionId

    fun userId(): UserId = userId

    fun type(): CreditTransactionType = type

    fun source(): CreditSource = source

    fun amount(): Long = amount

    fun balanceBefore(): Long = balanceBefore

    fun balanceAfter(): Long = balanceAfter

    fun referenceId(): String? = referenceId

    fun createdAt(): java.time.Instant? = createdAt
}
