package com.miyou.app.api.credit

import com.miyou.app.api.credit.dto.ChargeByPaymentRequest
import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.credit.usecase.CreditQueryUseCase
import com.miyou.app.domain.auth.model.AuthenticatedUser
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.port.PaymentGatewayPort
import com.miyou.app.fixture.CreditTransactionFixture
import com.miyou.app.fixture.UserCreditFixture
import com.miyou.app.fixture.UserIdFixture
import com.miyou.app.support.PermitAllSecurityTestConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Import(PermitAllSecurityTestConfig::class)
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
    @DisplayName("사용자의 크레딧 잔액을 반환한다")
    fun getBalance_returnsUserBalance() {
        // given
        val userId = UserIdFixture.create()

        `when`(creditChargeUseCase.initializeIfAbsent(userId)).thenReturn(Mono.empty())
        `when`(creditQueryUseCase.getBalance(userId))
            .thenReturn(Mono.just(UserCreditFixture.create(userId, 4900L)))

        // when & then
        webTestClient
            .get()
            .uri("/credit/balance?userId={id}", userId)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo(userId)
            .jsonPath("$.balance")
            .isEqualTo(4900)
    }

    @Test
    @DisplayName("사용자의 크레딧 거래 내역을 반환한다")
    fun getTransactions_returnsUserTransactionHistory() {
        // given
        val userId = UserIdFixture.create()
        val pageable = PageRequest.of(0, 20)
        val tx1 = CreditTransactionFixture.deduction(userId, 5000L)
        val tx2 = CreditTransactionFixture.signupBonus(userId, 5000L)

        `when`(creditQueryUseCase.getTransactions(userId, pageable)).thenReturn(Flux.just(tx1, tx2))

        // when & then
        webTestClient
            .get()
            .uri("/credit/transactions?userId={id}", userId)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(2)
    }

    @Test
    @DisplayName("결제를 승인하고 크레딧 충전 거래를 생성한다")
    fun chargeByPayment_confirmsPaymentAndCreatesChargeTransaction() {
        // given
        val userId = UserIdFixture.create()
        val request = ChargeByPaymentRequest(userId, "paykey-001", "order-001", "toss", 10000L)
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

        // when & then
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

    @Test
    @DisplayName(
        "인증된 사용자의 userId를 쿼리 파라미터의 userId보다 우선하여 사용한다 (익명 및 소셜 로그인 공존 상황)"
    )
    fun getBalance_authenticatedPrincipal_overridesQueryParamUserId() {
        // given: 인증 정보로 로그인된 유저 ID 설정
        val authenticatedUserId = "authenticated-user-1"
        val queryParamUserId = "different-anonymous-user"
        val principal = AuthenticatedUser(authenticatedUserId)

        `when`(creditChargeUseCase.initializeIfAbsent(authenticatedUserId)).thenReturn(Mono.empty())
        `when`(creditQueryUseCase.getBalance(authenticatedUserId))
            .thenReturn(Mono.just(UserCreditFixture.create(authenticatedUserId, 1234L)))

        // when & then: 쿼리 파라미터로 다른 유저 ID를 넘겨도 인증 객체의 유저 ID를 우선으로 응답받아야 함
        webTestClient
            .mutateWith(mockAuthentication(UsernamePasswordAuthenticationToken(principal, null, emptyList())))
            .get()
            .uri("/credit/balance?userId={id}", queryParamUserId)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo(authenticatedUserId)
            .jsonPath("$.balance")
            .isEqualTo(1234)
    }
}
