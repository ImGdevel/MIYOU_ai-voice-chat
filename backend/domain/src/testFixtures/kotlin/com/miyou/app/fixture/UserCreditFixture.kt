package com.miyou.app.fixture

import com.miyou.app.domain.credit.model.UserCredit

object UserCreditFixture {
    const val DEFAULT_BALANCE = 5000L
    const val DEFAULT_VERSION = 0L

    @JvmStatic
    fun create(): UserCredit = UserCredit.initialize(UserIdFixture.create(), DEFAULT_BALANCE)

    @JvmStatic
    fun create(userId: String): UserCredit = UserCredit.initialize(userId, DEFAULT_BALANCE)

    @JvmStatic
    fun create(
        userId: String,
        balance: Long,
    ): UserCredit = UserCredit(userId, balance, DEFAULT_VERSION)

    @JvmStatic
    fun withZeroBalance(userId: String): UserCredit = UserCredit(userId, 0L, DEFAULT_VERSION)

    @JvmStatic
    fun withBalance(balance: Long): UserCredit = UserCredit(UserIdFixture.create(), balance, DEFAULT_VERSION)
}
