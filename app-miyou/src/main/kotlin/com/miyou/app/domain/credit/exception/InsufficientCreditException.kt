package com.miyou.app.domain.credit.exception

import com.miyou.app.domain.dialogue.model.UserId

class InsufficientCreditException(
    val userId: UserId,
    val currentBalance: Long,
    val requiredAmount: Long,
) : RuntimeException(
        "Insufficient balance. userId=$userId, currentBalance=$currentBalance, requiredAmount=$requiredAmount"
    ) {
    constructor(
        userId: String,
        currentBalance: Long,
        requiredAmount: Long,
    ) : this(UserId.of(userId), currentBalance, requiredAmount)
}
