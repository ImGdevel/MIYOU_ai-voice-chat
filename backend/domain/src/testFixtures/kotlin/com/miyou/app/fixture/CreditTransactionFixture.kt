package com.miyou.app.fixture

import com.miyou.app.domain.credit.model.ConversationDeduction
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.SignupBonus

object CreditTransactionFixture {
    const val DEFAULT_AMOUNT = 100L

    @JvmStatic
    fun deduction(
        userId: String,
        balanceBefore: Long,
    ): CreditTransaction =
        CreditTransaction.of(
            userId,
            CreditTransactionType.DEDUCT,
            ConversationDeduction(ConversationSessionFixture.createId().value),
            DEFAULT_AMOUNT,
            balanceBefore,
            balanceBefore - DEFAULT_AMOUNT,
        )

    @JvmStatic
    fun signupBonus(
        userId: String,
        bonusAmount: Long,
    ): CreditTransaction =
        CreditTransaction.of(
            userId,
            CreditTransactionType.CHARGE,
            SignupBonus,
            bonusAmount,
            0L,
            bonusAmount,
        )
}
