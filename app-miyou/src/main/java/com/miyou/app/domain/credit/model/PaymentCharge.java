package com.miyou.app.domain.credit.model;

public record PaymentCharge(
	String paymentId,
	String pgProvider) implements CreditSource {

	@Override
	public CreditSourceType sourceType() {
		return CreditSourceType.PAYMENT_CHARGE;
	}
}
