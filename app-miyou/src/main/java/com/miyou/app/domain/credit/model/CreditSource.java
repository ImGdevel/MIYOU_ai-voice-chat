package com.miyou.app.domain.credit.model;


public sealed interface CreditSource permits
	ConversationDeduction,
	SignupBonus,
	PaymentCharge,
	MissionReward {

	CreditSourceType sourceType();
}
