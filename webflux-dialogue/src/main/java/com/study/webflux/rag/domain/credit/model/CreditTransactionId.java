package com.study.webflux.rag.domain.credit.model;

import java.util.UUID;

public record CreditTransactionId(
	String value
) {
	public CreditTransactionId {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("creditTransactionId cannot be null or blank");
		}
	}

	public static CreditTransactionId of(String value) {
		return new CreditTransactionId(value);
	}

	public static CreditTransactionId generate() {
		return new CreditTransactionId(UUID.randomUUID().toString());
	}
}
