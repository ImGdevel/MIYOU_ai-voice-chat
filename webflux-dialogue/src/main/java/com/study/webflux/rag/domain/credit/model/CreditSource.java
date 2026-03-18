package com.study.webflux.rag.domain.credit.model;


public sealed interface CreditSource permits
	ConversationDeduction,
	SignupBonus,
	PaymentCharge,
	MissionReward {

	CreditSourceType sourceType();
}
