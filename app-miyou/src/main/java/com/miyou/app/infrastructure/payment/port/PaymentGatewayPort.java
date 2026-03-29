package com.miyou.app.infrastructure.payment.port;

import reactor.core.publisher.Mono;

/**
 * 결제 대행사(PG) 연동 포트.
 * 새 PG사를 추가할 경우 이 인터페이스를 구현하고 {@link com.miyou.app.infrastructure.payment.config.PaymentGatewayConfiguration}에 빈으로 등록합니다.
 */
public interface PaymentGatewayPort {

	/** PG사 식별자 (예: "toss", "kakao", "stripe") */
	String getProviderName();

	/** 결제 승인을 요청합니다. */
	Mono<PaymentConfirmResult> confirmPayment(PaymentConfirmRequest request);

	record PaymentConfirmRequest(
		String paymentKey,
		String orderId,
		long amount
	) {
	}

	record PaymentConfirmResult(
		String paymentId,
		String orderId,
		long amount,
		String status
	) {
	}
}
