package com.study.webflux.rag.domain.memory.port;

import reactor.core.publisher.Mono;

public interface ConversationCounterPort {
	Mono<Long> increment();

	Mono<Long> get();

	Mono<Void> reset();
}
