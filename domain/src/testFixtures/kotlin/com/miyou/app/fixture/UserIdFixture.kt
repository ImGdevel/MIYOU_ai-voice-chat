package com.miyou.app.fixture

object UserIdFixture {
    const val DEFAULT_USER_ID = "user-1"

    @JvmStatic
    fun create(): String = DEFAULT_USER_ID

    @JvmStatic
    fun create(userId: String): String = userId
}
