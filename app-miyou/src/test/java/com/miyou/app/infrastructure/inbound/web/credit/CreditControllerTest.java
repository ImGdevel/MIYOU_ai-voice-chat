package com.miyou.app.infrastructure.inbound.web.credit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.miyou.app.application.credit.usecase.CreditChargeUseCase;
import com.miyou.app.application.credit.usecase.CreditQueryUseCase;
import com.miyou.app.domain.credit.model.CreditTransaction;
import com.miyou.app.domain.credit.model.CreditTransactionType;
import com.miyou.app.domain.credit.model.PaymentCharge;
import com.miyou.app.domain.credit.model.UserCredit;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.fixture.CreditTransactionFixture;
import com.miyou.app.fixture.UserCreditFixture;
import com.miyou.app.fixture.UserIdFixture;
import com.miyou.app.infrastructure.inbound.web.credit.dto.ChargeByPaymentRequest;
import com.miyou.app.infrastructure.payment.port.PaymentGatewayPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(CreditController.class)
@DisplayName("CreditController WebFlux 테스트")
class CreditControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockitoBean
	private CreditQueryUseCase creditQueryUseCase;

	@MockitoBean
	private CreditChargeUseCase creditChargeUseCase;

	@MockitoBean(name = "toss")
	private PaymentGatewayPort tossGateway;

	// ── GET /credit/balance ────────────────────────────────────────────────

	@Test
	@DisplayName("GET /credit/balance > 유효한 userId로 조회하면 200과 잔액을 반환한다")
	void getBalance_valid_returns200WithBalance() {
		UserId userId = UserIdFixture.create();
		UserCredit credit = UserCreditFixture.create(userId, 4900L);
		when(creditQueryUseCase.getBalance(eq(userId))).thenReturn(Mono.just(credit));

		webTestClient.get()
			.uri("/credit/balance?userId={id}", userId.value())
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.userId").isEqualTo(userId.value())
			.jsonPath("$.balance").isEqualTo(4900);
	}

	@Test
	@DisplayName("GET /credit/balance > userId 파라미터가 없으면 400을 반환한다")
	void getBalance_missingUserId_returns400() {
		webTestClient.get()
			.uri("/credit/balance")
			.exchange()
			.expectStatus().isBadRequest();
	}

	@Test
	@DisplayName("GET /credit/balance > 크레딧 레코드가 없으면 잔액 0을 반환한다")
	void getBalance_noRecord_returnsZero() {
		UserId userId = UserIdFixture.create();
		UserCredit empty = UserCredit.initialize(userId, 0L);
		when(creditQueryUseCase.getBalance(eq(userId))).thenReturn(Mono.just(empty));

		webTestClient.get()
			.uri("/credit/balance?userId={id}", userId.value())
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.balance").isEqualTo(0);
	}

	// ── GET /credit/transactions ───────────────────────────────────────────

	@Test
	@DisplayName("GET /credit/transactions > 트랜잭션 목록을 최신순으로 반환한다")
	void getTransactions_returns200WithList() {
		UserId userId = UserIdFixture.create();
		CreditTransaction tx1 = CreditTransactionFixture.deduction(userId, 5000L);
		CreditTransaction tx2 = CreditTransactionFixture.signupBonus(userId, 5000L);
		when(creditQueryUseCase.getTransactions(eq(userId), any(PageRequest.class)))
			.thenReturn(Flux.just(tx1, tx2));

		webTestClient.get()
			.uri("/credit/transactions?userId={id}", userId.value())
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(2)
			.jsonPath("$[0].type").isEqualTo("DEDUCT")
			.jsonPath("$[0].amount").isEqualTo(100)
			.jsonPath("$[1].type").isEqualTo("CHARGE");
	}

	@Test
	@DisplayName("GET /credit/transactions > 트랜잭션이 없으면 빈 배열을 반환한다")
	void getTransactions_empty_returnsEmptyArray() {
		UserId userId = UserIdFixture.create();
		when(creditQueryUseCase.getTransactions(eq(userId), any()))
			.thenReturn(Flux.empty());

		webTestClient.get()
			.uri("/credit/transactions?userId={id}", userId.value())
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.length()").isEqualTo(0);
	}

	@Test
	@DisplayName("GET /credit/transactions > 페이지/사이즈 파라미터가 반영된다")
	void getTransactions_withPaging_passes() {
		UserId userId = UserIdFixture.create();
		when(creditQueryUseCase.getTransactions(
			eq(userId),
			eq(PageRequest.of(1, 5))))
			.thenReturn(Flux.empty());

		webTestClient.get()
			.uri("/credit/transactions?userId={id}&page=1&size=5", userId.value())
			.exchange()
			.expectStatus().isOk();
	}

	// ── POST /credit/charge/payment ────────────────────────────────────────

	@Test
	@DisplayName("POST /credit/charge/payment > 유효한 PG 결제 요청 시 201과 충전된 트랜잭션을 반환한다")
	void chargeByPayment_valid_returns201() {
		UserId userId = UserIdFixture.create();
		ChargeByPaymentRequest request = new ChargeByPaymentRequest(
			userId.value(), "paykey-001", "order-001", "toss", 10000L);

		when(tossGateway.confirmPayment(any()))
			.thenReturn(Mono.just(new PaymentGatewayPort.PaymentConfirmResult(
				"paykey-001", "order-001", 10000L, "DONE")));

		CreditTransaction tx = CreditTransaction.of(
			userId,
			CreditTransactionType.CHARGE,
			new PaymentCharge("paykey-001", "toss"),
			10000L,
			0L,
			10000L,
			"paykey-001");
		when(creditChargeUseCase.chargeByPayment(eq(userId), anyLong(), any(PaymentCharge.class)))
			.thenReturn(Mono.just(tx));

		webTestClient.post()
			.uri("/credit/charge/payment")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.exchange()
			.expectStatus().isCreated()
			.expectBody()
			.jsonPath("$.type").isEqualTo("CHARGE")
			.jsonPath("$.amount").isEqualTo(10000)
			.jsonPath("$.sourceType").isEqualTo("PAYMENT_CHARGE");
	}

	@Test
	@DisplayName("POST /credit/charge/payment > 지원하지 않는 PG사는 400을 반환한다")
	void chargeByPayment_unknownProvider_returns400() {
		UserId userId = UserIdFixture.create();
		ChargeByPaymentRequest request = new ChargeByPaymentRequest(
			userId.value(), "key", "order", "unknown-pg", 5000L);

		webTestClient.post()
			.uri("/credit/charge/payment")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.exchange()
			.expectStatus().isBadRequest();
	}

	@Test
	@DisplayName("POST /credit/charge/payment > amount가 0 이하인 요청은 400을 반환한다")
	void chargeByPayment_nonPositiveAmount_returns400() {
		UserId userId = UserIdFixture.create();
		ChargeByPaymentRequest request = new ChargeByPaymentRequest(
			userId.value(), "key", "order", "toss", 0L);

		webTestClient.post()
			.uri("/credit/charge/payment")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.exchange()
			.expectStatus().isBadRequest();
	}
}
