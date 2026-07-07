package com.miyou.app.domain.credit.model

import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.fixture.UserIdFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("UserCredit 도메인 모델")
class UserCreditTest {
    @Nested
    @DisplayName("initialize")
    inner class Initialize {
        @Test
        @DisplayName("초기화하면 잔액과 버전 0이 설정된다")
        fun initialize_setsBalanceAndVersionZero() {
            val userId = UserIdFixture.create()

            val credit = UserCredit.initialize(userId, 5000L)

            assertThat(credit.userId).isEqualTo(userId)
            assertThat(credit.balance).isEqualTo(5000L)
            assertThat(credit.version).isEqualTo(0L)
        }

        @Test
        @DisplayName("잔액이 0이어도 초기화할 수 있다")
        fun initialize_withZeroBalance() {
            val credit = UserCredit.initialize(UserIdFixture.create(), 0L)

            assertThat(credit.balance).isZero()
        }

        @Test
        @DisplayName("잔액이 음수이면 초기화할 수 없다")
        fun initialize_negativeBalance_throws() {
            assertThatThrownBy { UserCredit(UserIdFixture.create(), -1L, 0L) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("balance cannot be negative")
        }
    }

    @Nested
    @DisplayName("deduct")
    inner class Deduct {
        @Test
        @DisplayName("차감하면 감소된 잔액의 새 크레딧을 반환한다")
        fun deduct_sufficient_returnsNewCreditWithDeductedBalance() {
            val userId = UserIdFixture.create()
            val credit = UserCredit(userId, 5000L, 2L)

            val result = credit.deduct(100L)

            assertThat(result.userId).isEqualTo(userId)
            assertThat(result.balance).isEqualTo(4900L)
            assertThat(result.version).isEqualTo(2L)
        }

        @Test
        @DisplayName("잔액과 동일한 금액도 차감할 수 있다")
        fun deduct_exactBalance_successWithZero() {
            val credit = UserCredit(UserIdFixture.create(), 100L, 0L)

            val result = credit.deduct(100L)

            assertThat(result.balance).isZero()
        }

        @Test
        @DisplayName("잔액이 부족하면 예외가 발생하고 원본은 유지된다")
        fun deduct_insufficient_throwsAndOriginalUnchanged() {
            val userId = UserIdFixture.create("low-credit-user")
            val credit = UserCredit(userId, 50L, 0L)

            assertThatThrownBy { credit.deduct(100L) }
                .isInstanceOf(InsufficientCreditException::class.java)
                .hasMessageContaining("low-credit-user")
                .hasMessageContaining("50")
                .hasMessageContaining("100")

            assertThat(credit.balance).isEqualTo(50L)
            assertThat(credit.version).isEqualTo(0L)
        }

        @Test
        @DisplayName("잔액이 0이면 차감 시 예외가 발생한다")
        fun deduct_zeroBalance_throws() {
            val credit = UserCredit(UserIdFixture.create(), 0L, 0L)

            assertThatThrownBy { credit.deduct(1L) }
                .isInstanceOf(InsufficientCreditException::class.java)
        }

        @Test
        @DisplayName("차감은 원본 객체를 변경하지 않는다")
        fun deduct_isImmutable() {
            val original = UserCredit(UserIdFixture.create(), 5000L, 0L)

            val deducted = original.deduct(100L)

            assertThat(original.balance).isEqualTo(5000L)
            assertThat(original.version).isEqualTo(0L)
            assertThat(deducted).isNotSameAs(original)
        }
    }

    @Nested
    @DisplayName("charge")
    inner class Charge {
        @Test
        @DisplayName("충전하면 잔액이 증가한다")
        fun charge_increasesBalance() {
            val userId = UserIdFixture.create()
            val credit = UserCredit(userId, 1000L, 5L)

            val result = credit.charge(3000L)

            assertThat(result.balance).isEqualTo(4000L)
            assertThat(result.version).isEqualTo(5L)
        }

        @Test
        @DisplayName("충전 금액이 0이면 예외가 발생한다")
        fun charge_zeroAmount_throws() {
            val credit = UserCredit(UserIdFixture.create(), 1000L, 0L)

            assertThatThrownBy { credit.charge(0L) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("charge amount must be positive")
        }

        @Test
        @DisplayName("충전 금액이 음수이면 예외가 발생한다")
        fun charge_negativeAmount_throws() {
            val credit = UserCredit(UserIdFixture.create(), 1000L, 0L)

            assertThatThrownBy { credit.charge(-100L) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("충전은 원본 객체를 변경하지 않는다")
        fun charge_isImmutable() {
            val original = UserCredit(UserIdFixture.create(), 1000L, 0L)

            val charged = original.charge(500L)

            assertThat(original.balance).isEqualTo(1000L)
            assertThat(charged).isNotSameAs(original)
        }
    }
}
