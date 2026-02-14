package com.study.webflux.rag.fixture;

import com.study.webflux.rag.domain.dialogue.model.UserId;

public final class UserIdFixture {

	public static final String DEFAULT_USER_ID = "user-1";

	private UserIdFixture() {
	}

	public static UserId create() {
		return UserId.of(DEFAULT_USER_ID);
	}

	public static UserId create(String userId) {
		return UserId.of(userId);
	}
}
