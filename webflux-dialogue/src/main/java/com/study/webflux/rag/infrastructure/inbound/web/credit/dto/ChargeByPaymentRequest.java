package com.study.webflux.rag.infrastructure.inbound.web.credit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ChargeByPaymentRequest(
	@NotBlank String userId,
	@NotBlank String paymentKey,
	@NotBlank String orderId,
	@NotBlank String pgProvider,
	@Positive long amount
) {
}
