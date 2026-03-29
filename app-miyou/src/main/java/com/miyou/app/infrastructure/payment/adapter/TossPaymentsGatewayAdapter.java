package com.miyou.app.infrastructure.payment.adapter;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.miyou.app.infrastructure.payment.port.PaymentGatewayPort;
import reactor.core.publisher.Mono;

/**
 * 토스페이먼츠 PG 어댑터 (stub 구현).
 * 실제 운영 연동 시 Toss Payments REST API를 호출하도록 구현합니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.toss.enabled", havingValue = "true", matchIfMissing = false)
public class TossPaymentsGatewayAdapter implements PaymentGatewayPort {

	@Override
	public String getProviderName() {
		return "toss";
	}

	@Override
	public Mono<PaymentConfirmResult> confirmPayment(PaymentConfirmRequest request) {
		// TODO: 토스페이먼츠 결제 승인 API 연동
		log.warn("TossPaymentsGatewayAdapter는 아직 stub 구현입니다. paymentKey={}", request.paymentKey());
		return Mono.just(new PaymentConfirmResult(
			request.paymentKey(),
			request.orderId(),
			request.amount(),
			"DONE"));
	}
}
