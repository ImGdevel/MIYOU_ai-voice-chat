package com.miyou.app.domain.credit.model;

import com.miyou.app.domain.credit.exception.InsufficientCreditException;
import com.miyou.app.domain.dialogue.model.UserId;

public record UserCredit(
	UserId userId,
	long balance,
	long version
) {
	public UserCredit {
		if (userId == null) {
			throw new IllegalArgumentException("userId cannot be null");
		}
		if (balance < 0) {
			throw new IllegalArgumentException("balance cannot be negative");
		}
	}

	public UserCredit deduct(long amount) {
		if (balance < amount) {
			throw new InsufficientCreditException(userId, balance, amount);
		}
		return new UserCredit(userId, balance - amount, version);
	}

	public UserCredit charge(long amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("charge amount must be positive");
		}
		return new UserCredit(userId, balance + amount, version);
	}

	public static UserCredit initialize(UserId userId, long initialBalance) {
		return new UserCredit(userId, initialBalance, 0L);
	}
}
