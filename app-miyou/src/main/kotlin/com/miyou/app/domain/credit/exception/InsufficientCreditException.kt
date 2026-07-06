package com.miyou.app.domain.credit.exception

class InsufficientCreditException(
    val userId: String,
    val currentBalance: Long,
    val requiredAmount: Long,
) : RuntimeException(
        "Insufficient balance. userId=$userId, currentBalance=$currentBalance, requiredAmount=$requiredAmount"
    )
