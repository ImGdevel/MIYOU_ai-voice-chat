package com.study.webflux.rag.domain.credit.model;

public record SignupBonus() implements CreditSource {

	@Override
	public CreditSourceType sourceType() {
		return CreditSourceType.SIGNUP_BONUS;
	}
}
