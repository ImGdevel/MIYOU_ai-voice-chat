package com.miyou.app.domain.credit.model

import com.miyou.app.domain.credit.exception.InsufficientCreditException

/**
 * 사용자의 크레딧 잔액을 관리하는 도메인 모델.
 */
data class UserCredit(
    val userId: String,
    val balance: Long,
    val version: Long,
) {
    init {
        require(userId.isNotBlank()) { "userId cannot be blank" }
        require(userId.length <= 128) { "userId cannot be longer than 128 characters" }
        require(balance >= 0) { "balance cannot be negative" }
    }

    /**
     * 사용자의 크레딧 잔액에서 특정 금액을 차감합니다.
     *
     * @param amount 차감할 크레딧 금액
     * @return 차감이 반영된 새로운 [UserCredit] 인스턴스
     * @throws InsufficientCreditException 잔액이 부족한 경우 발생
     */
    fun deduct(amount: Long): UserCredit {
        require(amount > 0) { "deduct amount must be positive" }
        if (balance < amount) {
            throw InsufficientCreditException(userId, balance, amount)
        }
        return UserCredit(userId, balance - amount, version)
    }

    /**
     * 사용자의 크레딧 잔액을 충전합니다.
     *
     * @param amount 충전할 크레딧 금액
     * @return 충전이 반영된 새로운 [UserCredit] 인스턴스
     */
    fun charge(amount: Long): UserCredit {
        require(amount > 0) { "charge amount must be positive" }
        return UserCredit(userId, balance + amount, version)
    }

    companion object {
        /**
         * 사용자의 최초 크레딧 잔액을 설정하여 초기 인스턴스를 생성합니다.
         *
         * @param userId 사용자 ID
         * @param initialBalance 초기 지급할 크레딧 잔액
         * @return 초기화된 [UserCredit] 인스턴스
         */
        @JvmStatic
        fun initialize(
            userId: String,
            initialBalance: Long,
        ): UserCredit {
            require(initialBalance >= 0) { "initial balance cannot be negative" }
            return UserCredit(userId, initialBalance, 0L)
        }
    }
}
