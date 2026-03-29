package com.miyou.app.fixture;

import com.miyou.app.domain.credit.model.CreditTransaction;
import com.miyou.app.domain.credit.model.CreditTransactionType;
import com.miyou.app.domain.credit.model.ConversationDeduction;
import com.miyou.app.domain.credit.model.SignupBonus;
import com.miyou.app.domain.dialogue.model.UserId;

public final class CreditTransactionFixture {

	public static final long DEFAULT_AMOUNT = 100L;

	private CreditTransactionFixture() {
	}

	public static CreditTransaction deduction(UserId userId, long balanceBefore) {
		return CreditTransaction.of(
			userId,
			CreditTransactionType.DEDUCT,
			new ConversationDeduction(ConversationSessionFixture.createId()),
			DEFAULT_AMOUNT,
			balanceBefore,
			balanceBefore - DEFAULT_AMOUNT);
	}

	public static CreditTransaction signupBonus(UserId userId, long bonusAmount) {
		return CreditTransaction.of(
			userId,
			CreditTransactionType.CHARGE,
			new SignupBonus(),
			bonusAmount,
			0L,
			bonusAmount);
	}
}
