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
    @DisplayName("초기화 (initialize)")
    inner class Initialize {
        @Test
        @DisplayName("초기화하면 잔액과 버전 0이 설정된다")
        fun initialize_setsBalanceAndVersionZero() {
            // given
            val userId = UserIdFixture.create()

            // when
            val credit = UserCredit.initialize(userId, 5000L)

            // then: 유저 ID, 초기 잔액, 버전 0 설정 확인
            assertThat(credit.userId).isEqualTo(userId)
            assertThat(credit.balance).isEqualTo(5000L)
            assertThat(credit.version).isEqualTo(0L)
        }

        @Test
        @DisplayName("잔액이 0이어도 초기화할 수 있다")
        fun initialize_withZeroBalance() {
            // when
            val credit = UserCredit.initialize(UserIdFixture.create(), 0L)

            // then: 잔액 0 확인
            assertThat(credit.balance).isZero()
        }

        @Test
        @DisplayName("잔액이 음수이면 초기화할 수 없다")
        fun initialize_negativeBalance_throws() {
            // when & then: 음수 잔액으로 생성 시 IllegalArgumentException 예외 확인
            assertThatThrownBy { UserCredit(UserIdFixture.create(), -1L, 0L) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("balance cannot be negative")
        }
    }

    @Nested
    @DisplayName("차감 (deduct)")
    inner class Deduct {
        @Test
        @DisplayName("차감하면 감소된 잔액의 새 크레딧을 반환한다")
        fun deduct_sufficient_returnsNewCreditWithDeductedBalance() {
            // given
            val userId = UserIdFixture.create()
            val credit = UserCredit(userId, 5000L, 2L)

            // when: 100 크레딧 차감
            val result = credit.deduct(100L)

            // then: 차감된 잔액 검증 (불변 객체이므로 버전은 그대로 유지됨)
            assertThat(result.userId).isEqualTo(userId)
            assertThat(result.balance).isEqualTo(4900L)
            assertThat(result.version).isEqualTo(2L)
        }

        @Test
        @DisplayName("잔액과 동일한 금액도 차감할 수 있다")
        fun deduct_exactBalance_successWithZero() {
            // given
            val credit = UserCredit(UserIdFixture.create(), 100L, 0L)

            // when: 잔액과 같은 100 크레딧 차감
            val result = credit.deduct(100L)

            // then: 잔액 0 확인
            assertThat(result.balance).isZero()
        }

        @Test
        @DisplayName("잔액이 부족하면 예외가 발생하고 원본은 유지된다")
        fun deduct_insufficient_throwsAndOriginalUnchanged() {
            // given: 50 크레딧을 가진 원본 객체
            val userId = UserIdFixture.create("low-credit-user")
            val credit = UserCredit(userId, 50L, 0L)

            // when & then: 100 크레딧 차감 시 InsufficientCreditException 예외 발생 검증
            assertThatThrownBy { credit.deduct(100L) }
                .isInstanceOf(InsufficientCreditException::class.java)
                .hasMessageContaining("low-credit-user")
                .hasMessageContaining("50")
                .hasMessageContaining("100")

            // 원본 객체 상태 유지 검증
            assertThat(credit.balance).isEqualTo(50L)
            assertThat(credit.version).isEqualTo(0L)
        }

        @Test
        @DisplayName("잔액이 0이면 차감 시 예외가 발생한다")
        fun deduct_zeroBalance_throws() {
            // given
            val credit = UserCredit(UserIdFixture.create(), 0L, 0L)

            // when & then: 잔액 0 상태에서 1 차감 시 InsufficientCreditException 예외 발생 검증
            assertThatThrownBy { credit.deduct(1L) }
                .isInstanceOf(InsufficientCreditException::class.java)
        }

        @Test
        @DisplayName("차감은 원본 객체를 변경하지 않는다")
        fun deduct_isImmutable() {
            // given
            val original = UserCredit(UserIdFixture.create(), 5000L, 0L)

            // when
            val deducted = original.deduct(100L)

            // then: 불변 객체 특성(원본 값 무변화 및 주소값 불일치) 검증
            assertThat(original.balance).isEqualTo(5000L)
            assertThat(original.version).isEqualTo(0L)
            assertThat(deducted).isNotSameAs(original)
        }
    }

    @Nested
    @DisplayName("충전 (charge)")
    inner class Charge {
        @Test
        @DisplayName("충전하면 잔액이 증가한다")
        fun charge_increasesBalance() {
            // given
            val userId = UserIdFixture.create()
            val credit = UserCredit(userId, 1000L, 5L)

            // when: 3000 크레딧 충전
            val result = credit.charge(3000L)

            // then: 합산 잔액 검증
            assertThat(result.balance).isEqualTo(4000L)
            assertThat(result.version).isEqualTo(5L)
        }

        @Test
        @DisplayName("충전 금액이 0이면 예외가 발생한다")
        fun charge_zeroAmount_throws() {
            // given
            val credit = UserCredit(UserIdFixture.create(), 1000L, 0L)

            // when & then: 0원 충전 시 IllegalArgumentException 발생 검증
            assertThatThrownBy { credit.charge(0L) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("charge amount must be positive")
        }

        @Test
        @DisplayName("충전 금액이 음수이면 예외가 발생한다")
        fun charge_negativeAmount_throws() {
            // given
            val credit = UserCredit(UserIdFixture.create(), 1000L, 0L)

            // when & then: 음수 충전 시 IllegalArgumentException 발생 검증
            assertThatThrownBy { credit.charge(-100L) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("충전은 원본 객체를 변경하지 않는다")
        fun charge_isImmutable() {
            // given
            val original = UserCredit(UserIdFixture.create(), 1000L, 0L)

            // when
            val charged = original.charge(500L)

            // then: 불변 객체 특성 검증
            assertThat(original.balance).isEqualTo(1000L)
            assertThat(charged).isNotSameAs(original)
        }
    }
}
