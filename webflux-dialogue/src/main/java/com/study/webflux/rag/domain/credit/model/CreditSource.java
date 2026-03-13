package com.study.webflux.rag.domain.credit.model;

import com.study.webflux.rag.domain.credit.model.source.ConversationDeduction;
import com.study.webflux.rag.domain.credit.model.source.MissionReward;
import com.study.webflux.rag.domain.credit.model.source.PaymentCharge;
import com.study.webflux.rag.domain.credit.model.source.SignupBonus;

public sealed interface CreditSource permits
	ConversationDeduction,
	SignupBonus,
	PaymentCharge,
	MissionReward {

	CreditSourceType sourceType();
}
