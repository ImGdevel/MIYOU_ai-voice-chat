package com.study.webflux.rag.domain.credit.model;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;

public record ConversationDeduction(
	ConversationSessionId sessionId
) implements CreditSource {

	@Override
	public CreditSourceType sourceType() {
		return CreditSourceType.CONVERSATION_DEDUCTION;
	}
}
