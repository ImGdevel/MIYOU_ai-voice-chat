package com.miyou.app.infrastructure.inbound.web.credit.dto;

import com.miyou.app.domain.credit.model.UserCredit;

public record UserCreditResponse(
	String userId,
	long balance
) {
	public static UserCreditResponse from(UserCredit credit) {
		return new UserCreditResponse(credit.userId().value(), credit.balance());
	}
}
