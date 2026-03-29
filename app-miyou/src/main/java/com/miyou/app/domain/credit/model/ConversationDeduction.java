package com.miyou.app.domain.credit.model;

import com.miyou.app.domain.dialogue.model.ConversationSessionId;

public record ConversationDeduction(
	ConversationSessionId sessionId) implements CreditSource {

	@Override
	public CreditSourceType sourceType() {
		return CreditSourceType.CONVERSATION_DEDUCTION;
	}
}
