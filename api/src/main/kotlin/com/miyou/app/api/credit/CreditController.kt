package com.miyou.app.api.credit

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.credit.usecase.CreditQueryUseCase
import com.miyou.app.domain.auth.model.AuthenticatedUser
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.exception.CreditErrorCode
import com.miyou.app.api.common.UserIdResolver
import com.miyou.app.api.credit.dto.ChargeByPaymentRequest
import com.miyou.app.api.credit.dto.CreditTransactionResponse
import com.miyou.app.api.credit.dto.UserCreditResponse
import com.miyou.app.domain.credit.port.PaymentGatewayPort
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
        @RequestParam(required = false) userId: String?,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Mono<UserCreditResponse> =
        UserIdResolver
            .resolve(principal, userId)
            .flatMap { resolvedUserId ->
                creditChargeUseCase
                    .initializeIfAbsent(resolvedUserId)
                    .then(creditQueryUseCase.getBalance(resolvedUserId))
            }.map(UserCreditResponse::from)

    @GetMapping("/transactions")
    fun getTransactions(
        @RequestParam(required = false) userId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Flux<CreditTransactionResponse> =
        UserIdResolver
            .resolve(principal, userId)
            .flatMapMany { resolvedUserId ->
                creditQueryUseCase.getTransactions(
                    resolvedUserId,
                    PageRequest.of(page, size),
                )
            }.map(CreditTransactionResponse::from)

    @PostMapping("/charge/payment")
    @ResponseStatus(HttpStatus.CREATED)
    fun chargeByPayment(
        @Valid @RequestBody request: ChargeByPaymentRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Mono<CreditTransactionResponse> {
        val gateway =
            paymentGatewayMap[request.pgProvider]
                ?: return Mono.error(
                    ResponseStatusException(
                        CreditErrorCode.UNSUPPORTED_PAYMENT_PROVIDER.httpStatus,
                        CreditErrorCode.UNSUPPORTED_PAYMENT_PROVIDER.message,
                    ),
                )

        return UserIdResolver
            .resolve(principal, request.userId)
            .flatMap { resolvedUserId ->
                gateway
                    .confirmPayment(
                        PaymentGatewayPort.PaymentConfirmRequest(
                            request.paymentKey,
                            request.orderId,
                            request.amount,
                        ),
                    ).flatMap { result ->
                        creditChargeUseCase.chargeByPayment(
                            resolvedUserId,
                            result.amount,
                            PaymentCharge(result.paymentId, request.pgProvider),
                        )
                    }
            }.map(CreditTransactionResponse::from)
    }
}
