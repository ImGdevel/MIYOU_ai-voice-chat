package com.study.webflux.rag.domain.credit.model.source;

import com.study.webflux.rag.domain.credit.model.CreditSource;
import com.study.webflux.rag.domain.credit.model.CreditSourceType;

public record PaymentCharge(
	String paymentId,
	String pgProvider
) implements CreditSource {

	@Override
	public CreditSourceType sourceType() {
		return CreditSourceType.PAYMENT_CHARGE;
	}
}
