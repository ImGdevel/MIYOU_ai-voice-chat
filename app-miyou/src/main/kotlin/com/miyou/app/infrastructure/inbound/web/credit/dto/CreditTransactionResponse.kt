package com.miyou.app.infrastructure.inbound.web.credit.dto

import com.miyou.app.domain.credit.model.CreditTransaction
import java.time.Instant

data class CreditTransactionResponse(
    val transactionId: String,
    val userId: String,
    val type: String,
    val sourceType: String,
    val amount: Long,
    val balanceBefore: Long,
    val balanceAfter: Long,
    val referenceId: String?,
    val createdAt: Instant?,
) {
    companion object {
        fun from(tx: CreditTransaction): CreditTransactionResponse =
            CreditTransactionResponse(
                tx.transactionId.value,
                tx.userId.value,
                tx.type.name,
                tx.source.sourceType().name,
                tx.amount,
                tx.balanceBefore,
                tx.balanceAfter,
                tx.referenceId,
                tx.createdAt,
            )
    }
}
