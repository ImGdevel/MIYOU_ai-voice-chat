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
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
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
@DisplayName("CreditController WebFlux 테스트")
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
    @DisplayName("잔액 조회 성공")
    fun getBalance_valid_returns200WithBalance() {
        val userId = UserIdFixture.create()
        `when`(creditQueryUseCase.getBalance(eq(userId)))
            .thenReturn(Mono.just(UserCreditFixture.create(userId, 4900L)))

        webTestClient
            .get()
            .uri("/credit/balance?userId={id}", userId.value())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo(userId.value())
            .jsonPath("$.balance")
            .isEqualTo(4900)
    }

    @Test
    @DisplayName("트랜잭션 목록 조회 성공")
    fun getTransactions_returns200WithList() {
        val userId = UserIdFixture.create()
        val tx1 = CreditTransactionFixture.deduction(userId, 5000L)
        val tx2 = CreditTransactionFixture.signupBonus(userId, 5000L)
        `when`(creditQueryUseCase.getTransactions(eq(userId), any(PageRequest::class.java)))
            .thenReturn(Flux.just(tx1, tx2))

        webTestClient
            .get()
            .uri("/credit/transactions?userId={id}", userId.value())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(2)
            .jsonPath("$[0].type")
            .isEqualTo("DEDUCT")
            .jsonPath("$[1].type")
            .isEqualTo("CHARGE")
    }

    @Test
    @DisplayName("결제 충전 성공")
    fun chargeByPayment_valid_returns201() {
        val userId = UserIdFixture.create()
        val request = ChargeByPaymentRequest(userId.value(), "paykey-001", "order-001", "toss", 10000L)
        `when`(tossGateway.confirmPayment(any()))
            .thenReturn(Mono.just(PaymentGatewayPort.PaymentConfirmResult("paykey-001", "order-001", 10000L, "DONE")))

        val tx: CreditTransaction =
            CreditTransaction.of(
                userId,
                CreditTransactionType.CHARGE,
                PaymentCharge("paykey-001", "toss"),
                10000L,
                0L,
                10000L,
                "paykey-001",
            )
        `when`(creditChargeUseCase.chargeByPayment(eq(userId), anyLong(), any(PaymentCharge::class.java)))
            .thenReturn(Mono.just(tx))

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
