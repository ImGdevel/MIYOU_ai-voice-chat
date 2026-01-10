package com.study.webflux.rag.domain.memory.port;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import reactor.core.publisher.Mono;

public interface ConversationCounterPort {
	Mono<Long> increment(UserId userId);

	Mono<Long> get(UserId userId);

	Mono<Void> reset(UserId userId);
}
