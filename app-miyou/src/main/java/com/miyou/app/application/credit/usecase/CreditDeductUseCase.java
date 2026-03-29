package com.miyou.app.application.credit.usecase;

import com.miyou.app.domain.credit.model.CreditTransaction;
import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.dialogue.model.UserId;
import reactor.core.publisher.Mono;

public interface CreditDeductUseCase {

	Mono<CreditTransaction> deductForConversation(UserId userId, ConversationSessionId sessionId);
}
