package com.miyou.app.fixture

import com.miyou.app.domain.dialogue.model.UserId

object UserIdFixture {

	const val DEFAULT_USER_ID = "user-1"

	@JvmStatic
	fun create(): UserId = UserId.of(DEFAULT_USER_ID)

	@JvmStatic
	fun create(userId: String): UserId = UserId.of(userId)
}
