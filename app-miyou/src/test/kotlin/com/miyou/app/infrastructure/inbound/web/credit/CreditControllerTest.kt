package com.miyou.app.infrastructure.inbound.web.credit

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.credit.usecase.CreditQueryUseCase
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.fixture.CreditTransactionFixture
import com.miyou.app.fixture.UserCreditFixture
import com.miyou.app.fixture.UserIdFixture
import com.miyou.app.infrastructure.inbound.web.credit.dto.ChargeByPaymentRequest
import com.miyou.app.infrastructure.payment.port.PaymentGatewayPort
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@WebFluxTest(CreditController::class)
class CreditControllerTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var creditQueryUseCase: CreditQueryUseCase

    @MockitoBean
    private lateinit var creditChargeUseCase: CreditChargeUseCase

    @MockitoBean(name = "toss")
    private lateinit var tossGateway: PaymentGatewayPort

    @Test
    @DisplayName("getBalance returns the user's balance")
    fun getBalance_returnsUserBalance() {
        val userId = UserIdFixture.create()

        `when`(creditQueryUseCase.getBalance(userId))
            .thenReturn(Mono.just(UserCreditFixture.create(userId, 4900L)))

        webTestClient
            .get()
            .uri("/credit/balance?userId={id}", userId.value)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo(userId.value)
            .jsonPath("$.balance")
            .isEqualTo(4900)
    }

    @Test
    @DisplayName("getTransactions returns the user's transaction history")
    fun getTransactions_returnsUserTransactionHistory() {
        val userId = UserIdFixture.create()
        val pageable = PageRequest.of(0, 20)
        val tx1 = CreditTransactionFixture.deduction(userId, 5000L)
        val tx2 = CreditTransactionFixture.signupBonus(userId, 5000L)

        `when`(creditQueryUseCase.getTransactions(userId, pageable)).thenReturn(Flux.just(tx1, tx2))

        webTestClient
            .get()
            .uri("/credit/transactions?userId={id}", userId.value)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(2)
    }

    @Test
    @DisplayName("chargeByPayment confirms the payment and creates a charge transaction")
    fun chargeByPayment_confirmsPaymentAndCreatesChargeTransaction() {
        val userId = UserIdFixture.create()
        val request = ChargeByPaymentRequest(userId.value, "paykey-001", "order-001", "toss", 10000L)
        val confirmRequest = PaymentGatewayPort.PaymentConfirmRequest("paykey-001", "order-001", 10000L)
        val confirmed = PaymentGatewayPort.PaymentConfirmResult("paykey-001", "order-001", 10000L, "DONE")
        val transaction: CreditTransaction =
            CreditTransaction.of(
                userId,
                CreditTransactionType.CHARGE,
                PaymentCharge("paykey-001", "toss"),
                10000L,
                0L,
                10000L,
                "paykey-001",
            )

        `when`(tossGateway.confirmPayment(confirmRequest)).thenReturn(Mono.just(confirmed))
        `when`(creditChargeUseCase.chargeByPayment(userId, 10000L, PaymentCharge("paykey-001", "toss")))
            .thenReturn(Mono.just(transaction))

        webTestClient
            .post()
            .uri("/credit/charge/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("CHARGE")
            .jsonPath("$.amount")
            .isEqualTo(10000)
            .jsonPath("$.sourceType")
            .isEqualTo("PAYMENT_CHARGE")
    }
}
