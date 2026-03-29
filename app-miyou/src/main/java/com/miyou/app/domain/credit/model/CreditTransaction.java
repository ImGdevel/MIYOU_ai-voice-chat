package com.miyou.app.domain.credit.model;

import java.time.Instant;

import com.miyou.app.domain.dialogue.model.UserId;

public record CreditTransaction(
	CreditTransactionId transactionId,
	UserId userId,
	CreditTransactionType type,
	CreditSource source,
	long amount,
	long balanceBefore,
	long balanceAfter,
	String referenceId,
	Instant createdAt
) {
	public CreditTransaction {
		if (transactionId == null) {
			throw new IllegalArgumentException("transactionId cannot be null");
		}
		if (userId == null) {
			throw new IllegalArgumentException("userId cannot be null");
		}
		if (type == null) {
			throw new IllegalArgumentException("type cannot be null");
		}
		if (source == null) {
			throw new IllegalArgumentException("source cannot be null");
		}
		if (amount <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public static CreditTransaction of(UserId userId,
		CreditTransactionType type,
		CreditSource source,
		long amount,
		long balanceBefore,
		long balanceAfter) {
		return new CreditTransaction(
			CreditTransactionId.generate(),
			userId,
			type,
			source,
			amount,
			balanceBefore,
			balanceAfter,
			null,
			Instant.now());
	}

	public static CreditTransaction of(UserId userId,
		CreditTransactionType type,
		CreditSource source,
		long amount,
		long balanceBefore,
		long balanceAfter,
		String referenceId) {
		return new CreditTransaction(
			CreditTransactionId.generate(),
			userId,
			type,
			source,
			amount,
			balanceBefore,
			balanceAfter,
			referenceId,
			Instant.now());
	}
}
