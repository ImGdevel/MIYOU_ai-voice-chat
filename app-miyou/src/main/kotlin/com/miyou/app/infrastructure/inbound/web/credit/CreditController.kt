package com.miyou.app.infrastructure.inbound.web.credit

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.credit.usecase.CreditQueryUseCase
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.infrastructure.inbound.web.credit.dto.ChargeByPaymentRequest
import com.miyou.app.infrastructure.inbound.web.credit.dto.CreditTransactionResponse
import com.miyou.app.infrastructure.inbound.web.credit.dto.UserCreditResponse
import com.miyou.app.infrastructure.payment.port.PaymentGatewayPort
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/credit")
class CreditController(
    private val creditQueryUseCase: CreditQueryUseCase,
    private val creditChargeUseCase: CreditChargeUseCase,
    private val paymentGatewayMap: Map<String, PaymentGatewayPort>,
) {
    @GetMapping("/balance")
    fun getBalance(
        @RequestParam @NotBlank userId: String,
    ): Mono<UserCreditResponse> {
        val resolvedUserId = UserId.of(userId)
        return creditChargeUseCase
            .initializeIfAbsent(resolvedUserId)
            .then(creditQueryUseCase.getBalance(resolvedUserId))
            .map(UserCreditResponse::from)
    }

    @GetMapping("/transactions")
    fun getTransactions(
        @RequestParam @NotBlank userId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Flux<CreditTransactionResponse> =
        creditQueryUseCase
            .getTransactions(
                UserId.of(userId),
                PageRequest.of(page, size),
            ).map(CreditTransactionResponse::from)

    @PostMapping("/charge/payment")
    @ResponseStatus(HttpStatus.CREATED)
    fun chargeByPayment(
        @Valid @RequestBody request: ChargeByPaymentRequest,
    ): Mono<CreditTransactionResponse> {
        val gateway =
            paymentGatewayMap[request.pgProvider]
                ?: return Mono.error(
                    ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "지원하지 않는 PG 제공자입니다: ${request.pgProvider}",
                    ),
                )

        return gateway
            .confirmPayment(
                PaymentGatewayPort.PaymentConfirmRequest(
                    request.paymentKey,
                    request.orderId,
                    request.amount,
                ),
            ).flatMap { result ->
                creditChargeUseCase.chargeByPayment(
                    UserId.of(request.userId),
                    result.amount,
                    PaymentCharge(result.paymentId, request.pgProvider),
                )
            }.map(CreditTransactionResponse::from)
    }
}
