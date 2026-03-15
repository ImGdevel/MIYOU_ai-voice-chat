package com.study.webflux.rag.infrastructure.inbound.web.credit.dto;

import java.time.Instant;

import com.study.webflux.rag.domain.credit.model.CreditTransaction;

public record CreditTransactionResponse(
	String transactionId,
	String userId,
	String type,
	String sourceType,
	long amount,
	long balanceBefore,
	long balanceAfter,
	String referenceId,
	Instant createdAt
) {
	public static CreditTransactionResponse from(CreditTransaction tx) {
		return new CreditTransactionResponse(
			tx.transactionId().value(),
			tx.userId().value(),
			tx.type().name(),
			tx.source().sourceType().name(),
			tx.amount(),
			tx.balanceBefore(),
			tx.balanceAfter(),
			tx.referenceId(),
			tx.createdAt());
	}
}
