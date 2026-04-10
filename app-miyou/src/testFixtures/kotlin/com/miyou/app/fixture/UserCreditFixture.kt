package com.miyou.app.fixture

import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.domain.dialogue.model.UserId

object UserCreditFixture {
    const val DEFAULT_BALANCE = 5000L
    const val DEFAULT_VERSION = 0L

    @JvmStatic
    fun create(): UserCredit = UserCredit.initialize(UserIdFixture.create(), DEFAULT_BALANCE)

    @JvmStatic
    fun create(userId: UserId): UserCredit = UserCredit.initialize(userId, DEFAULT_BALANCE)

    @JvmStatic
    fun create(
        userId: UserId,
        balance: Long,
    ): UserCredit = UserCredit(userId, balance, DEFAULT_VERSION)

    @JvmStatic
    fun withZeroBalance(userId: UserId): UserCredit = UserCredit(userId, 0L, DEFAULT_VERSION)

    @JvmStatic
    fun withBalance(balance: Long): UserCredit = UserCredit(UserIdFixture.create(), balance, DEFAULT_VERSION)
}
