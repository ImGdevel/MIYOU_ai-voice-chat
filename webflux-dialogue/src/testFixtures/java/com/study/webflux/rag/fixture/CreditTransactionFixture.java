package com.study.webflux.rag.fixture;

import com.study.webflux.rag.domain.credit.model.CreditTransaction;
import com.study.webflux.rag.domain.credit.model.CreditTransactionType;
import com.study.webflux.rag.domain.credit.model.source.ConversationDeduction;
import com.study.webflux.rag.domain.credit.model.source.SignupBonus;
import com.study.webflux.rag.domain.dialogue.model.UserId;

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
