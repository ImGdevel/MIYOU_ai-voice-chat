package com.study.webflux.rag.infrastructure.inbound.web.credit;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.study.webflux.rag.application.credit.usecase.CreditChargeUseCase;
import com.study.webflux.rag.application.credit.usecase.CreditQueryUseCase;
import com.study.webflux.rag.domain.credit.model.PaymentCharge;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.infrastructure.inbound.web.credit.dto.ChargeByPaymentRequest;
import com.study.webflux.rag.infrastructure.inbound.web.credit.dto.CreditTransactionResponse;
import com.study.webflux.rag.infrastructure.inbound.web.credit.dto.UserCreditResponse;
import com.study.webflux.rag.infrastructure.payment.port.PaymentGatewayPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/** 크레딧 잔액 조회 및 충전 엔드포인트 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/credit")
public class CreditController {

	private final CreditQueryUseCase creditQueryUseCase;
	private final CreditChargeUseCase creditChargeUseCase;
	private final Map<String, PaymentGatewayPort> paymentGatewayMap;

	/** 유저의 현재 크레딧 잔액을 조회합니다. */
	@GetMapping("/balance")
	public Mono<UserCreditResponse> getBalance(@RequestParam @NotBlank String userId) {
		return creditQueryUseCase.getBalance(UserId.of(userId))
			.map(UserCreditResponse::from);
	}

	/** 유저의 크레딧 변경 내역을 최신순으로 조회합니다. */
	@GetMapping("/transactions")
	public Flux<CreditTransactionResponse> getTransactions(
		@RequestParam @NotBlank String userId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size) {
		return creditQueryUseCase.getTransactions(UserId.of(userId), PageRequest.of(page, size))
			.map(CreditTransactionResponse::from);
	}

	/** PG 결제를 통해 크레딧을 충전합니다. */
	@PostMapping("/charge/payment")
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<CreditTransactionResponse> chargeByPayment(
		@Valid @RequestBody ChargeByPaymentRequest request) {
		PaymentGatewayPort gateway = paymentGatewayMap.get(request.pgProvider());
		if (gateway == null) {
			return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"지원하지 않는 PG사입니다: " + request.pgProvider()));
		}
		return gateway.confirmPayment(
			new PaymentGatewayPort.PaymentConfirmRequest(
				request.paymentKey(),
				request.orderId(),
				request.amount()))
			.flatMap(result -> creditChargeUseCase.chargeByPayment(
				UserId.of(request.userId()),
				result.amount(),
				new PaymentCharge(result.paymentId(), request.pgProvider())))
			.map(CreditTransactionResponse::from);
	}
}
