package com.study.webflux.rag.domain.credit.model.source;

import com.study.webflux.rag.domain.credit.model.CreditSource;
import com.study.webflux.rag.domain.credit.model.CreditSourceType;

public record SignupBonus() implements CreditSource {

	@Override
	public CreditSourceType sourceType() {
		return CreditSourceType.SIGNUP_BONUS;
	}
}
