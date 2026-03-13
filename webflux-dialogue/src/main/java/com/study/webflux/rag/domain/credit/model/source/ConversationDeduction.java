package com.study.webflux.rag.domain.credit.model.source;

import com.study.webflux.rag.domain.credit.model.CreditSource;
import com.study.webflux.rag.domain.credit.model.CreditSourceType;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;

public record ConversationDeduction(
	ConversationSessionId sessionId
) implements CreditSource {

	@Override
	public CreditSourceType sourceType() {
		return CreditSourceType.CONVERSATION_DEDUCTION;
	}
}
