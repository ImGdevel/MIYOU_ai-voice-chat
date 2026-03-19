package com.study.webflux.rag.application.credit.usecase;

import com.study.webflux.rag.domain.credit.model.CreditTransaction;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import reactor.core.publisher.Mono;

public interface CreditDeductUseCase {

	Mono<CreditTransaction> deductForConversation(UserId userId, ConversationSessionId sessionId);
}
