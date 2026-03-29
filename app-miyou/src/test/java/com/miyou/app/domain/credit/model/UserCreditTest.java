package com.miyou.app.domain.credit.model;

import com.miyou.app.domain.credit.exception.InsufficientCreditException;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.fixture.UserIdFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserCredit 도메인 모델")
class UserCreditTest {

	@Nested
	@DisplayName("initialize()")
	class Initialize {

		@Test
		@DisplayName("초기화 시 지정된 잔액과 버전 0으로 생성된다")
		void initialize_setsBalanceAndVersionZero() {
			UserId userId = UserIdFixture.create();

			UserCredit credit = UserCredit.initialize(userId, 5000L);

			assertThat(credit.userId()).isEqualTo(userId);
			assertThat(credit.balance()).isEqualTo(5000L);
			assertThat(credit.version()).isEqualTo(0L);
		}

		@Test
		@DisplayName("잔액 0으로 초기화 가능하다")
		void initialize_withZeroBalance() {
			UserCredit credit = UserCredit.initialize(UserIdFixture.create(), 0L);

			assertThat(credit.balance()).isZero();
		}

		@Test
		@DisplayName("userId가 null이면 예외가 발생한다")
		void initialize_nullUserId_throws() {
			assertThatThrownBy(() -> new UserCredit(null, 1000L, 0L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("userId cannot be null");
		}

		@Test
		@DisplayName("잔액이 음수이면 예외가 발생한다")
		void initialize_negativeBalance_throws() {
			assertThatThrownBy(() -> new UserCredit(UserIdFixture.create(), -1L, 0L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("balance cannot be negative");
		}
	}

	@Nested
	@DisplayName("deduct()")
	class Deduct {

		@Test
		@DisplayName("잔액 충분 시 차감 후 새로운 UserCredit 반환")
		void deduct_sufficient_returnsNewCreditWithDeductedBalance() {
			UserId userId = UserIdFixture.create();
			UserCredit credit = new UserCredit(userId, 5000L, 2L);

			UserCredit result = credit.deduct(100L);

			assertThat(result.userId()).isEqualTo(userId);
			assertThat(result.balance()).isEqualTo(4900L);
			assertThat(result.version()).isEqualTo(2L);
		}

		@Test
		@DisplayName("잔액과 차감액이 정확히 같으면 잔액 0으로 성공한다")
		void deduct_exactBalance_successWithZero() {
			UserCredit credit = new UserCredit(UserIdFixture.create(), 100L, 0L);

			UserCredit result = credit.deduct(100L);

			assertThat(result.balance()).isZero();
		}

		@Test
		@DisplayName("잔액 부족 시 InsufficientCreditException 발생 — 원본 객체 불변성 유지")
		void deduct_insufficient_throwsAndOriginalUnchanged() {
			UserId userId = UserIdFixture.create("low-credit-user");
			UserCredit credit = new UserCredit(userId, 50L, 0L);

			assertThatThrownBy(() -> credit.deduct(100L))
				.isInstanceOf(InsufficientCreditException.class)
				.hasMessageContaining("크레딧이 부족합니다")
				.hasMessageContaining("low-credit-user")
				.hasMessageContaining("50")
				.hasMessageContaining("100");

			// 원본 불변성 확인
			assertThat(credit.balance()).isEqualTo(50L);
			assertThat(credit.version()).isEqualTo(0L);
		}

		@Test
		@DisplayName("잔액 0에서 차감 시도 시 예외 발생")
		void deduct_zeroBalance_throws() {
			UserCredit credit = new UserCredit(UserIdFixture.create(), 0L, 0L);

			assertThatThrownBy(() -> credit.deduct(1L))
				.isInstanceOf(InsufficientCreditException.class);
		}

		@Test
		@DisplayName("deduct는 불변 — 원본 객체를 변경하지 않는다")
		void deduct_isImmutable() {
			UserCredit original = new UserCredit(UserIdFixture.create(), 5000L, 0L);

			UserCredit deducted = original.deduct(100L);

			assertThat(original.balance()).isEqualTo(5000L);
			assertThat(original.version()).isEqualTo(0L);
			assertThat(deducted).isNotSameAs(original);
		}
	}

	@Nested
	@DisplayName("charge()")
	class Charge {

		@Test
		@DisplayName("충전 후 잔액 증가")
		void charge_increasesBalance() {
			UserId userId = UserIdFixture.create();
			UserCredit credit = new UserCredit(userId, 1000L, 5L);

			UserCredit result = credit.charge(3000L);

			assertThat(result.balance()).isEqualTo(4000L);
			assertThat(result.version()).isEqualTo(5L);
		}

		@Test
		@DisplayName("0 크레딧 충전 시 예외 발생")
		void charge_zeroAmount_throws() {
			UserCredit credit = new UserCredit(UserIdFixture.create(), 1000L, 0L);

			assertThatThrownBy(() -> credit.charge(0L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("charge amount must be positive");
		}

		@Test
		@DisplayName("음수 크레딧 충전 시 예외 발생")
		void charge_negativeAmount_throws() {
			UserCredit credit = new UserCredit(UserIdFixture.create(), 1000L, 0L);

			assertThatThrownBy(() -> credit.charge(-100L))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("charge는 불변 — 원본 객체를 변경하지 않는다")
		void charge_isImmutable() {
			UserCredit original = new UserCredit(UserIdFixture.create(), 1000L, 0L);

			UserCredit charged = original.charge(500L);

			assertThat(original.balance()).isEqualTo(1000L);
			assertThat(charged).isNotSameAs(original);
		}
	}
}
