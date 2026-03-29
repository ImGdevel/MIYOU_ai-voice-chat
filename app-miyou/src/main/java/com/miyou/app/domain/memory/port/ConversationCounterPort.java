package com.miyou.app.domain.memory.port;

import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import reactor.core.publisher.Mono;

public interface ConversationCounterPort {

	Mono<Long> increment(ConversationSessionId sessionId);

	Mono<Long> get(ConversationSessionId sessionId);

	Mono<Void> reset(ConversationSessionId sessionId);
}
