package com.miyou.app.domain.credit.model

import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.dialogue.model.UserId

data class UserCredit(
    val userId: UserId,
    val balance: Long,
    val version: Long,
) {
    init {
        require(balance >= 0) { "balance cannot be negative" }
    }

    fun deduct(amount: Long): UserCredit {
        require(amount > 0) { "deduct amount must be positive" }
        if (balance < amount) {
            throw InsufficientCreditException(userId, balance, amount)
        }
        return UserCredit(userId, balance - amount, version)
    }

    fun charge(amount: Long): UserCredit {
        require(amount > 0) { "charge amount must be positive" }
        return UserCredit(userId, balance + amount, version)
    }

    companion object {
        @JvmStatic
        fun initialize(
            userId: UserId,
            initialBalance: Long,
        ): UserCredit {
            require(initialBalance >= 0) { "initial balance cannot be negative" }
            return UserCredit(userId, initialBalance, 0L)
        }
    }

    fun userId(): UserId = userId

    fun balance(): Long = balance

    fun version(): Long = version
}
