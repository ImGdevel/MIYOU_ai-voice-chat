package com.study.webflux.rag.domain.credit.exception;

import com.study.webflux.rag.domain.dialogue.model.UserId;

public class InsufficientCreditException extends RuntimeException {

	private final String userId;
	private final long currentBalance;
	private final long requiredAmount;

	public InsufficientCreditException(UserId userId, long currentBalance, long requiredAmount) {
		super(String.format("크레딧이 부족합니다. userId=%s, 현재 잔액=%d, 필요 크레딧=%d",
			userId.value(), currentBalance, requiredAmount));
		this.userId = userId.value();
		this.currentBalance = currentBalance;
		this.requiredAmount = requiredAmount;
	}

	public String getUserId() {
		return userId;
	}

	public long getCurrentBalance() {
		return currentBalance;
	}

	public long getRequiredAmount() {
		return requiredAmount;
	}
}
