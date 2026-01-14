package com.study.webflux.rag.domain.memory.port;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import reactor.core.publisher.Mono;

public interface ConversationCounterPort {

	Mono<Long> increment(ConversationSessionId sessionId);

	Mono<Long> get(ConversationSessionId sessionId);

	Mono<Void> reset(ConversationSessionId sessionId);
}
