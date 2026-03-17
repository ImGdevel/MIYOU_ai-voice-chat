package com.study.webflux.rag.fixture;

import com.study.webflux.rag.domain.credit.model.UserCredit;
import com.study.webflux.rag.domain.dialogue.model.UserId;

public final class UserCreditFixture {

	public static final long DEFAULT_BALANCE = 5000L;
	public static final long DEFAULT_VERSION = 0L;

	private UserCreditFixture() {
	}

	public static UserCredit create() {
		return UserCredit.initialize(UserIdFixture.create(), DEFAULT_BALANCE);
	}

	public static UserCredit create(UserId userId) {
		return UserCredit.initialize(userId, DEFAULT_BALANCE);
	}

	public static UserCredit create(UserId userId, long balance) {
		return new UserCredit(userId, balance, DEFAULT_VERSION);
	}

	public static UserCredit withZeroBalance(UserId userId) {
		return new UserCredit(userId, 0L, DEFAULT_VERSION);
	}

	public static UserCredit withBalance(long balance) {
		return new UserCredit(UserIdFixture.create(), balance, DEFAULT_VERSION);
	}
}
