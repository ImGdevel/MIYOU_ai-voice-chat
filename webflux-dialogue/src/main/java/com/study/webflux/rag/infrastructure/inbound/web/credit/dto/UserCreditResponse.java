package com.study.webflux.rag.infrastructure.inbound.web.credit.dto;

import com.study.webflux.rag.domain.credit.model.UserCredit;

public record UserCreditResponse(
	String userId,
	long balance
) {
	public static UserCreditResponse from(UserCredit credit) {
		return new UserCreditResponse(credit.userId().value(), credit.balance());
	}
}
